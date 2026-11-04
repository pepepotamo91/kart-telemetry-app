package com.ander.karttelemetry.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kart-frame G forces from phone sensors, robust to engine vibration, any
 * mounting angle and a steel chassis next to the phone.
 *
 * - **Anti-aliasing.** Every raw sample (up to ~200 Hz) goes through a
 *   two-pole low-pass (≈1.5 Hz) before anything is read out. The engine shakes the phone
 *   at 30–70 Hz; sampling the last value 10×/s, as before, folded that
 *   vibration into fake readings of ±1.5 g with the kart parked.
 * - **No compass.** The magnetometer is disturbed by the frame and engine,
 *   so the heading it gives can't be trusted. Nothing here uses it.
 * - **Lateral = speed × yaw rate** (gyroscope projected on the vertical).
 *   Needs no calibration and is immune to vibration and mount angle.
 * - **Longitudinal = accelerometer along the kart's forward axis.** For a
 *   rigidly mounted phone the forward axis is fixed in the phone's own
 *   frame. It is found automatically while driving by matching the
 *   horizontal acceleration against GPS speed changes (and the yaw-rate
 *   lateral). Until that has converged, GPS speed change is used instead.
 *
 * Pure Kotlin (no Android types) so it can be tested off-device. Times are
 * in nanoseconds (SensorEvent.timestamp / elapsedRealtimeNanos).
 */
class GForceEstimator(
    /** Initial forward-axis guess from a previous session: up vector + angle. */
    savedCalibration: Calibration? = null,
) {
    data class Calibration(val upX: Double, val upY: Double, val upZ: Double, val angleRad: Double)

    // Outputs (g units). Lateral: + = turning right.
    var gLongitudinal = 0.0; private set
    var gLateral = 0.0; private set
    var yawRateDps = 0.0; private set
    val isCalibrated: Boolean get() = calibrated

    // --- gravity / horizontal basis
    private var upX = 0.0; private var upY = 0.0; private var upZ = 1.0
    private var haveUp = false
    private var basisUp: DoubleArray? = null
    private val u1 = DoubleArray(3)
    private val u2 = DoubleArray(3)

    // --- per-sample two-pole low-pass state (body frame)
    private var winStartNs = -1L
    private val acc1 = DoubleArray(3); private val acc2 = DoubleArray(3); private var accNs = -1L
    private val gyr1 = DoubleArray(3); private val gyr2 = DoubleArray(3); private var gyrNs = -1L
    private var accSeen = false; private var gyrSeen = false

    // --- filtered values
    private var fGpsLong = 0.0
    private var lastWindowNs = -1L

    // --- GPS
    private var speedMps = 0.0
    private var lastFixNs = -1L
    private var lastFixSpeed = 0.0
    private var gpsLong = 0.0

    // --- calibration accumulators over the current GPS interval
    private var intMRe = 0.0; private var intMIm = 0.0
    private var intLeft = 0.0; private var intN = 0

    // Running sums (with forgetting) for the forward-axis fit.
    private var sRe = 0.0; private var sIm = 0.0
    private var sumE2 = 0.0; private var sumM2 = 0.0
    private var calibrated = false
    private var angle = 0.0 // forward axis angle in the (u1, u2) basis

    init {
        if (savedCalibration != null) {
            val n = sqrt(savedCalibration.upX * savedCalibration.upX + savedCalibration.upY * savedCalibration.upY + savedCalibration.upZ * savedCalibration.upZ)
            if (n > 0.5) {
                upX = savedCalibration.upX / n; upY = savedCalibration.upY / n; upZ = savedCalibration.upZ / n
                setBasis()
                angle = savedCalibration.angleRad
                // Seed the fit so a matching mount is usable at once but a new
                // mount angle quickly overrides it.
                sRe = SEED_WEIGHT * cos(angle); sIm = SEED_WEIGHT * sin(angle)
                sumE2 = SEED_WEIGHT; sumM2 = SEED_WEIGHT
                calibrated = true
            }
        }
    }

    /** Current fit, to save for the next session (null until calibrated). */
    fun calibration(): Calibration? {
        val b = basisUp ?: return null
        return if (calibrated) Calibration(b[0], b[1], b[2], angle) else null
    }

    fun onGravity(x: Double, y: Double, z: Double, tNs: Long) {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-3) return
        val nx = x / n; val ny = y / n; val nz = z / n
        if (!haveUp) {
            upX = nx; upY = ny; upZ = nz; haveUp = true
        } else {
            // ~2 s time constant: follows a remount, ignores braking pitch.
            val a = 0.01
            upX += a * (nx - upX); upY += a * (ny - upY); upZ += a * (nz - upZ)
            val m = sqrt(upX * upX + upY * upY + upZ * upZ)
            upX /= m; upY /= m; upZ /= m
        }
        val b = basisUp
        if (b == null) {
            setBasis()
        } else {
            val dot = b[0] * upX + b[1] * upY + b[2] * upZ
            if (dot < cos(Math.toRadians(REMOUNT_DEG))) {
                // Phone has been re-mounted: start the forward-axis fit again.
                setBasis()
                resetFit()
            }
        }
    }

    fun onLinearAccel(x: Double, y: Double, z: Double, tNs: Long) {
        tick(tNs)
        accNs = lowPass(acc1, acc2, x, y, z, tNs, accNs, accSeen)
        accSeen = true
    }

    fun onGyro(x: Double, y: Double, z: Double, tNs: Long) {
        tick(tNs)
        gyrNs = lowPass(gyr1, gyr2, x, y, z, tNs, gyrNs, gyrSeen)
        gyrSeen = true
    }

    private fun lowPass(s1: DoubleArray, s2: DoubleArray, x: Double, y: Double, z: Double, tNs: Long, prevNs: Long, seen: Boolean): Long {
        if (!seen) {
            s1[0] = x; s1[1] = y; s1[2] = z
            s2[0] = x; s2[1] = y; s2[2] = z
            return tNs
        }
        val dt = ((tNs - prevNs) / 1e9).coerceIn(0.0, 0.1)
        val a = 1 - exp(-dt / FILTER_TAU_S)
        s1[0] += a * (x - s1[0]); s1[1] += a * (y - s1[1]); s1[2] += a * (z - s1[2])
        s2[0] += a * (s1[0] - s2[0]); s2[1] += a * (s1[1] - s2[1]); s2[2] += a * (s1[2] - s2[2])
        return tNs
    }

    /** GPS fix with Doppler speed. */
    fun onGpsFix(speedMps: Double, tNs: Long) {
        val prevNs = lastFixNs
        val prevSpeed = lastFixSpeed
        this.speedMps = speedMps
        lastFixNs = tNs
        lastFixSpeed = speedMps
        if (prevNs < 0) return
        val dt = (tNs - prevNs) / 1e9
        if (dt < 0.3 || dt > 2.5) {
            clearInterval()
            return
        }
        gpsLong = (speedMps - prevSpeed) / dt / G

        // One calibration sample per GPS interval: mean horizontal accel (body
        // frame, complex) against the expected kart-frame accel (long + i·left).
        if (intN >= 3 && basisUp != null && prevSpeed > MIN_CAL_SPEED && speedMps > MIN_CAL_SPEED) {
            val mRe = intMRe / intN; val mIm = intMIm / intN
            val eRe = gpsLong; val eIm = intLeft / intN
            val e2 = eRe * eRe + eIm * eIm
            if (e2 > MIN_EXCITATION_G * MIN_EXCITATION_G) {
                // m ≈ e·exp(i·angle)  →  Σ m·conj(e) has argument = angle
                sRe = FORGET * sRe + (mRe * eRe + mIm * eIm)
                sIm = FORGET * sIm + (mIm * eRe - mRe * eIm)
                sumE2 = FORGET * sumE2 + e2
                sumM2 = FORGET * sumM2 + (mRe * mRe + mIm * mIm)
                val rho = hypot(sRe, sIm) / sqrt(sumE2 * sumM2)
                if (sumE2 > MIN_TOTAL_EXCITATION && rho > MIN_FIT_QUALITY) {
                    angle = atan2(sIm, sRe)
                    calibrated = true
                }
            }
        }
        clearInterval()
    }

    private fun clearInterval() {
        intMRe = 0.0; intMIm = 0.0; intLeft = 0.0; intN = 0
    }

    private fun resetFit() {
        sRe = 0.0; sIm = 0.0; sumE2 = 0.0; sumM2 = 0.0
        calibrated = false
    }

    private fun setBasis() {
        val up = doubleArrayOf(upX, upY, upZ)
        // u1 = the phone axis least aligned with vertical, projected on the horizontal.
        val ref = when {
            abs(upX) <= abs(upY) && abs(upX) <= abs(upZ) -> doubleArrayOf(1.0, 0.0, 0.0)
            abs(upY) <= abs(upZ) -> doubleArrayOf(0.0, 1.0, 0.0)
            else -> doubleArrayOf(0.0, 0.0, 1.0)
        }
        val d = ref[0] * up[0] + ref[1] * up[1] + ref[2] * up[2]
        for (i in 0..2) u1[i] = ref[i] - d * up[i]
        val n = sqrt(u1[0] * u1[0] + u1[1] * u1[1] + u1[2] * u1[2])
        for (i in 0..2) u1[i] /= n
        // u2 = up × u1  (90° to the left of u1 seen from above)
        u2[0] = up[1] * u1[2] - up[2] * u1[1]
        u2[1] = up[2] * u1[0] - up[0] * u1[2]
        u2[2] = up[0] * u1[1] - up[1] * u1[0]
        basisUp = up
    }

    private fun tick(tNs: Long) {
        if (winStartNs < 0) winStartNs = tNs
        if (tNs - winStartNs >= WINDOW_NS) {
            closeWindow(tNs)
            winStartNs = tNs
        }
    }

    private fun closeWindow(tNs: Long) {
        val b = basisUp
        val dtWin = if (lastWindowNs < 0) WINDOW_NS / 1e9 else ((tNs - lastWindowNs) / 1e9).coerceIn(0.005, 0.5)
        lastWindowNs = tNs
        // Only the GPS fallback (1 Hz steps) needs extra smoothing.
        val alphaGps = 1 - exp(-dtWin / GPS_TAU_S)

        // Yaw rate about the vertical (+ = counter-clockwise from above = left turn).
        var leftG = 0.0
        if (gyrSeen && b != null) {
            val wx = gyr2[0]; val wy = gyr2[1]; val wz = gyr2[2]
            val wUp = wx * b[0] + wy * b[1] + wz * b[2]
            yawRateDps = Math.toDegrees(wUp)
            leftG = speedMps * wUp / G
        }
        // Mean horizontal accel in the (u1, u2) basis.
        var mRe = 0.0; var mIm = 0.0
        if (accSeen && b != null) {
            val ax = acc2[0]; val ay = acc2[1]; val az = acc2[2]
            mRe = (ax * u1[0] + ay * u1[1] + az * u1[2]) / G
            mIm = (ax * u2[0] + ay * u2[1] + az * u2[2]) / G
            intMRe += mRe; intMIm += mIm; intLeft += leftG; intN++
        }

        // Longitudinal: accelerometer along the fitted forward axis, else GPS.
        fGpsLong += alphaGps * (gpsLong - fGpsLong)
        gLongitudinal = if (calibrated) mRe * cos(angle) + mIm * sin(angle) else fGpsLong
        gLateral = -leftG
    }

    companion object {
        private const val G = 9.80665
        private const val WINDOW_NS = 50_000_000L
        private const val FILTER_TAU_S = 0.07
        private const val GPS_TAU_S = 0.4
        private const val REMOUNT_DEG = 15.0
        private const val MIN_CAL_SPEED = 3.0       // m/s
        private const val MIN_EXCITATION_G = 0.05
        private const val MIN_TOTAL_EXCITATION = 0.4 // Σ|e|² in g²
        private const val MIN_FIT_QUALITY = 0.6
        private const val FORGET = 0.995
        private const val SEED_WEIGHT = 0.3
    }
}

/** Keeps the fitted forward axis between sessions (same mount → G usable at once). */
class GCalibrationStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("g_calibration", android.content.Context.MODE_PRIVATE)

    fun load(): GForceEstimator.Calibration? {
        if (!prefs.contains("angle")) return null
        return GForceEstimator.Calibration(
            prefs.getFloat("up_x", 0f).toDouble(),
            prefs.getFloat("up_y", 0f).toDouble(),
            prefs.getFloat("up_z", 1f).toDouble(),
            prefs.getFloat("angle", 0f).toDouble(),
        )
    }

    fun save(c: GForceEstimator.Calibration) {
        prefs.edit()
            .putFloat("up_x", c.upX.toFloat())
            .putFloat("up_y", c.upY.toFloat())
            .putFloat("up_z", c.upZ.toFloat())
            .putFloat("angle", c.angleRad.toFloat())
            .apply()
    }
}
