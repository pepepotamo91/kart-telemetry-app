package com.ander.karttelemetry.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Estimates the RPM of a single-cylinder 4-stroke engine from its sound.
 *
 * Same method as the Predator 200 acoustic analysis: a single cylinder fires
 * once every two revolutions, so its sound is a family of harmonics spaced by
 * the firing frequency f_exp and rpm = 120 · f_exp. The fundamental itself is
 * usually lost (phone mics cut below ~50 Hz), so instead of looking for a peak
 * we test every candidate spacing with a harmonic comb: the mean level at k·f
 * minus the mean level half-way between harmonics, (k+½)·f, on a whitened
 * spectrum. Comparing against the in-between points avoids octave errors.
 *
 * Pure Kotlin (no Android APIs) so it can be unit-tested off-device. Feed
 * samples already decimated to [sampleRate] (~2 kHz) with [push], then call
 * [analyze] once per hop (~100 ms).
 */
class RpmEstimator(
    private val sampleRate: Double,
    private val shortWindowS: Double = SHORT_WINDOW_S,
    private val longWindowS: Double = LONG_WINDOW_S,
    private val subharmonicRatio: Double = SUBHARMONIC_RATIO,
    private val jumpPenaltyDbPerHz: Double = JUMP_PENALTY_DB_PER_HZ,
) {

    data class Estimate(val rpm: Double, val confidenceDb: Double)

    // Two analysis windows: the long one resolves the closely spaced harmonics
    // at idle and is used to (re)acquire; once tracking above ~2000 rpm the
    // short one keeps the reading responsive while revving.
    private val shortSize = (sampleRate * shortWindowS).roundToInt()
    private val longSize = (sampleRate * longWindowS).roundToInt()
    private val fftSize = nextPow2(longSize * 4).coerceAtLeast(nextPow2(shortSize * 8))
    private val binHz = sampleRate / fftSize
    private val nBins = fftSize / 2 + 1

    private val hannShort = hann(shortSize)
    private val hannLong = hann(longSize)
    private val ring = DoubleArray(longSize)
    private var ringPos = 0
    private var filled = 0
    private var useLong = true

    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val bitRev = IntArray(fftSize).also { table ->
        val bits = Integer.numberOfTrailingZeros(fftSize)
        for (i in 0 until fftSize) table[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val cosTable = DoubleArray(fftSize / 2) { cos(2.0 * PI * it / fftSize) }
    private val sinTable = DoubleArray(fftSize / 2) { -sin(2.0 * PI * it / fftSize) }

    private val logMag = DoubleArray(nBins)
    private val prefix = DoubleArray(nBins + 1)
    private val white = DoubleArray(nBins)
    private val whitenHalfBins = (WHITEN_HALF_HZ / binHz).roundToInt().coerceAtLeast(1)

    private val nCandidates = ((F_MAX - F_MIN) / F_STEP).toInt() + 1
    private val scores = DoubleArray(nCandidates)

    // Tracking state.
    private var trackedF: Double? = null
    private var pendingF: Double? = null
    private var pendingCount = 0
    private var missedFrames = 0
    private val recent = ArrayDeque<Double>()
    private var smoothedRpm: Double? = null

    fun push(samples: DoubleArray, count: Int = samples.size) {
        for (i in 0 until count) {
            ring[ringPos] = samples[i]
            ringPos = (ringPos + 1) % longSize
        }
        filled = (filled + count).coerceAtMost(longSize)
    }

    fun reset() {
        filled = 0
        ringPos = 0
        useLong = true
        clearTrack()
    }

    /** Returns the smoothed RPM, or null while there is no confident reading. */
    fun analyze(): Estimate? {
        val tracked = trackedF
        useLong = when {
            tracked == null -> true
            useLong && tracked > SHORT_WINDOW_ABOVE_HZ + 1.0 -> false
            !useLong && tracked < SHORT_WINDOW_ABOVE_HZ - 1.0 -> true
            else -> useLong
        }
        val size = if (useLong) longSize else shortSize
        if (filled < size) return null
        computeWhitenedSpectrum(size, if (useLong) hannLong else hannShort)

        var bestIdx = -1
        var bestAdjusted = Double.NEGATIVE_INFINITY
        for (c in 0 until nCandidates) {
            val f = F_MIN + c * F_STEP
            val s = comb(f)
            scores[c] = s
            val adjusted = if (tracked != null) s - jumpPenaltyDbPerHz * abs(f - tracked) else s
            if (adjusted > bestAdjusted) {
                bestAdjusted = adjusted
                bestIdx = c
            }
        }
        if (bestIdx < 0) return miss()

        // Octave check: a comb at m·f only samples every m-th harmonic, so it can
        // score as well as the true spacing f. The true spacing is the one whose
        // sub-multiples score far worse (≈ 1/m), so if F/m still scores almost as
        // high as F, the real spacing is F/m.
        val fBest = F_MIN + bestIdx * F_STEP
        for (m in 4 downTo 2) {
            val sub = fBest / m
            if (sub < F_MIN) continue
            val subIdx = ((sub - F_MIN) / F_STEP).roundToInt().coerceIn(0, nCandidates - 1)
            var localBest = subIdx
            for (c in (subIdx - 2).coerceAtLeast(0)..(subIdx + 2).coerceAtMost(nCandidates - 1)) {
                if (scores[c] > scores[localBest]) localBest = c
            }
            if (scores[localBest] > subharmonicRatio * scores[bestIdx]) {
                bestIdx = localBest
                break
            }
        }

        val confidence = scores[bestIdx]
        // Acquiring from nothing needs a clearer pattern than holding a track.
        val minConfidence = if (tracked == null) ACQUIRE_CONFIDENCE_DB else MIN_CONFIDENCE_DB
        if (confidence < minConfidence) return miss()

        // Parabolic refinement between neighbouring candidates.
        var f = F_MIN + bestIdx * F_STEP
        if (bestIdx in 1 until nCandidates - 1) {
            val a = scores[bestIdx - 1]
            val b = scores[bestIdx]
            val c = scores[bestIdx + 1]
            val denom = a - 2 * b + c
            if (denom < 0) f += (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) * F_STEP
        }

        // A big jump away from the tracked value has to repeat on consecutive
        // frames before it is believed; a one-off is treated as noise.
        if (tracked != null && abs(f - tracked) > MAX_STEP_HZ) {
            val pending = pendingF
            if (pending != null && abs(f - pending) <= MAX_STEP_HZ) {
                pendingCount++
                pendingF = f
            } else {
                pendingF = f
                pendingCount = 1
            }
            if (pendingCount < JUMP_CONFIRM_FRAMES) return miss(keepTrack = true)
            recent.clear()
            smoothedRpm = null
        }
        pendingF = null
        pendingCount = 0
        trackedF = f
        missedFrames = 0

        recent.addLast(120.0 * f)
        while (recent.size > MEDIAN_LEN) recent.removeFirst()
        val median = recent.sorted()[recent.size / 2]
        val prevSmooth = smoothedRpm
        val smooth = if (prevSmooth == null) median else prevSmooth + EMA_ALPHA * (median - prevSmooth)
        smoothedRpm = smooth
        return Estimate(smooth, confidence)
    }

    private fun miss(keepTrack: Boolean = false): Estimate? {
        missedFrames++
        if (missedFrames >= MAX_MISSED_FRAMES) {
            clearTrack()
            return null
        }
        if (!keepTrack) {
            pendingF = null
            pendingCount = 0
        }
        // Hold the last value briefly so a single noisy frame doesn't blank the display.
        return smoothedRpm?.let { Estimate(it, 0.0) }
    }

    private fun clearTrack() {
        trackedF = null
        pendingF = null
        pendingCount = 0
        missedFrames = 0
        recent.clear()
        smoothedRpm = null
    }

    private fun computeWhitenedSpectrum(size: Int, window: DoubleArray) {
        // The newest [size] samples end just before ringPos.
        val start = (ringPos - size + longSize) % longSize
        var mean = 0.0
        for (i in 0 until size) mean += ring[(start + i) % longSize]
        mean /= size
        for (i in 0 until fftSize) {
            re[i] = if (i < size) (ring[(start + i) % longSize] - mean) * window[i] else 0.0
            im[i] = 0.0
        }
        fft()
        for (k in 0 until nBins) logMag[k] = 10.0 * log10(re[k] * re[k] + im[k] * im[k] + 1e-12)

        // Subtract a moving-average envelope so only the lines remain.
        prefix[0] = 0.0
        for (k in 0 until nBins) prefix[k + 1] = prefix[k] + logMag[k]
        for (k in 0 until nBins) {
            val lo = (k - whitenHalfBins).coerceAtLeast(0)
            val hi = (k + whitenHalfBins).coerceAtMost(nBins - 1)
            white[k] = logMag[k] - (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
        }
    }

    private fun valueAt(freq: Double): Double {
        val pos = freq / binHz
        val i = pos.toInt()
        if (i < 0 || i + 1 >= nBins) return 0.0
        val frac = pos - i
        return white[i] * (1 - frac) + white[i + 1] * frac
    }

    private fun comb(f: Double): Double {
        val kMin = ceil(BAND_LO_HZ / f).toInt().coerceAtLeast(1)
        val kMax = floor(BAND_HI_HZ / f).toInt()
        val n = kMax - kMin + 1
        if (n < MIN_HARMONICS) return Double.NEGATIVE_INFINITY
        var on = 0.0
        var off = 0.0
        for (k in kMin..kMax) {
            on += valueAt(k * f)
            off += valueAt((k + 0.5) * f)
        }
        return (on - off) / n
    }

    /** In-place iterative radix-2 FFT on [re]/[im]. */
    private fun fft() {
        for (i in 0 until fftSize) {
            val j = bitRev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= fftSize) {
            val half = len / 2
            val step = fftSize / len
            var start = 0
            while (start < fftSize) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                start += len
            }
            len *= 2
        }
    }

    companion object {
        /** Target sample rate for the decimated audio fed to [push]. */
        const val TARGET_SAMPLE_RATE = 2000.0

        private const val SHORT_WINDOW_S = 0.5
        private const val LONG_WINDOW_S = 1.0
        private const val SHORT_WINDOW_ABOVE_HZ = 16.5 // ≈ 2000 rpm

        // Firing frequency search range: 7.5–50 Hz → 900–6000 rpm.
        const val F_MIN = 7.5
        const val F_MAX = 50.0
        private const val F_STEP = 0.05

        private const val BAND_LO_HZ = 45.0
        private const val BAND_HI_HZ = 600.0
        private const val MIN_HARMONICS = 4
        private const val WHITEN_HALF_HZ = 12.0

        private const val MIN_CONFIDENCE_DB = 3.0
        private const val ACQUIRE_CONFIDENCE_DB = 7.0
        private const val JUMP_PENALTY_DB_PER_HZ = 0.25
        private const val SUBHARMONIC_RATIO = 0.9
        private const val MAX_STEP_HZ = 2.5 // ≈ 300 rpm in 0.1 s
        private const val JUMP_CONFIRM_FRAMES = 3
        private const val MAX_MISSED_FRAMES = 6
        private const val MEDIAN_LEN = 3
        private const val EMA_ALPHA = 0.6

        private fun hann(n: Int) = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / (n - 1)) }

        private fun nextPow2(n: Int): Int {
            var p = 1
            while (p < n) p = p shl 1
            return p
        }
    }
}
