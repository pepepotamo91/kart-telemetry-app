package com.ander.karttelemetry.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Low-pass FIR (windowed sinc, Hamming) + integer downsampling, to bring mic
 * audio at any native rate down to ~[RpmEstimator.TARGET_SAMPLE_RATE].
 * Only the kept outputs are computed, so the cost is taps × output rate.
 */
class Decimator(inputRate: Int, targetRate: Double = RpmEstimator.TARGET_SAMPLE_RATE) {

    val factor: Int = (inputRate / targetRate).roundToInt().coerceAtLeast(1)
    val outputRate: Double = inputRate.toDouble() / factor

    private val taps: DoubleArray
    private val history: DoubleArray
    private var pos = 0
    private var phase = 0

    init {
        val cutoffHz = 0.4 * outputRate
        var n = (inputRate * 0.02).roundToInt() or 1
        if (factor == 1) n = 1
        val fc = cutoffHz / inputRate
        val mid = n / 2
        taps = DoubleArray(n) { i ->
            val x = (i - mid).toDouble()
            val sinc = if (i == mid) 2 * fc else sin(2 * PI * fc * x) / (PI * x)
            val window = if (n == 1) 1.0 else 0.54 - 0.46 * cos(2 * PI * i / (n - 1))
            sinc * window
        }
        val gain = taps.sum()
        for (i in taps.indices) taps[i] /= gain
        history = DoubleArray(n)
    }

    /**
     * Filters [input] (PCM16) and writes decimated samples to [out].
     * Returns how many output samples were written.
     */
    fun process(input: ShortArray, count: Int, out: DoubleArray): Int {
        var written = 0
        val n = taps.size
        for (i in 0 until count) {
            history[pos] = input[i] / 32768.0
            pos = (pos + 1) % n
            phase++
            if (phase >= factor) {
                phase = 0
                var acc = 0.0
                var idx = pos
                for (t in 0 until n) {
                    acc += taps[t] * history[idx]
                    idx++
                    if (idx == n) idx = 0
                }
                if (written < out.size) out[written++] = acc
            }
        }
        return written
    }
}
