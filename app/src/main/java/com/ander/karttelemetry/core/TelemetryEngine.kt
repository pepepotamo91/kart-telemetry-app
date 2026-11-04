package com.ander.karttelemetry.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Owns the sensor/location listeners and derives everything the dashboard shows.
 * Lifecycle is tied to the activity being in the foreground: call [start] in
 * onResume and [stop] in onPause.
 */
class TelemetryEngine(
    private val context: Context,
    private val onState: (TelemetryUiState) -> Unit,
) : SensorEventListener, LocationListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gAvailable = gravitySensor != null && linearAccelSensor != null && gyroSensor != null

    private val calibrationStore = GCalibrationStore(context)
    private var gEstimator = GForceEstimator(calibrationStore.load())
    private var lastUiPushMs = 0L

    private val lapTracker = LapTracker()
    private var sessionStartElapsedMs = SystemClock.elapsedRealtime()

    private var recorder: SessionRecorder? = null
    private var lastRecordElapsedMs = 0L

    private var lastLocation: Location? = null
    private var lastBearingDeg: Float? = null

    private var currentSpeedKmh = 0.0
    private var maxSpeedKmh = 0.0

    private var currentGLateral = 0.0
    private var currentGLongitudinal = 0.0
    private var currentGTotal = 0.0
    private var maxGLateral = 0.0
    private var maxGTotal = 0.0

    private var lastLapMs: Long? = null
    private var bestLapMs: Long? = null

    private var currentRpm: Double? = null
    private var maxRpm = 0.0
    private val audioMonitor = EngineAudioMonitor(context) { rpm -> onRpm(rpm) }

    private val gearingStore = GearingStore(context)
    private var gearing = gearingStore.load()

    private val trackPoints = mutableListOf<GeoPoint>()
    private var trackSnapshot: List<GeoPoint> = emptyList()

    fun start() {
        // Fastest rate so the anti-alias filter sees the engine vibration
        // instead of a few aliased snapshots of it.
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            runCatching { locationManager.requestLocationUpdates(provider, 200L, 0f, this) }
        }

        audioMonitor.start()
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        pushState()
    }

    /** Call after the mic permission is granted while the engine is already running. */
    fun startAudio() {
        audioMonitor.start()
        pushState()
    }

    fun setGearing(newGearing: Gearing) {
        gearingStore.save(newGearing)
        gearing = gearingStore.load()
        pushState()
    }

    fun stop() {
        audioMonitor.stop()
        sensorManager.unregisterListener(this)
        gEstimator.calibration()?.let { calibrationStore.save(it) }
        runCatching { locationManager.removeUpdates(this) }
        recorder?.close()
        recorder = null
    }

    fun toggleRecording() {
        val active = recorder
        if (active != null) {
            active.close()
            recorder = null
        } else {
            val dir = File(context.getExternalFilesDir(null), "sessions").apply { mkdirs() }
            val name = "session_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
            recorder = SessionRecorder(File(dir, name))
        }
        pushState()
    }

    fun setFinishLine() {
        lastLocation?.let { lapTracker.setFinishLine(it) }
        pushState()
    }

    fun manualLap() {
        val now = SystemClock.elapsedRealtime()
        onLapCompleted(lapTracker.manualLap(now, sessionStartElapsedMs))
    }

    fun resetSession() {
        maxSpeedKmh = 0.0
        maxGLateral = 0.0
        maxGTotal = 0.0
        maxRpm = 0.0
        lastLapMs = null
        bestLapMs = null
        lapTracker.reset()
        trackPoints.clear()
        trackSnapshot = emptyList()
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        pushState()
    }

    private fun onLapCompleted(lapTimeMs: Long) {
        lastLapMs = lapTimeMs
        val best = bestLapMs
        if (best == null || lapTimeMs < best) bestLapMs = lapTimeMs
        pushState()
    }

    private fun onRpm(rpm: Double?) {
        currentRpm = rpm
        if (rpm != null && rpm > maxRpm) maxRpm = rpm
        pushState()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY ->
                gEstimator.onGravity(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), event.timestamp)

            Sensor.TYPE_GYROSCOPE ->
                gEstimator.onGyro(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), event.timestamp)

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                gEstimator.onLinearAccel(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), event.timestamp)
                updateGForce()
                maybeRecordSample()
            }
        }
        // Sensors arrive at up to 200 Hz; the screen only needs ~20 updates/s.
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastUiPushMs >= 50L) {
            lastUiPushMs = nowMs
            pushState()
        }
    }

    private fun updateGForce() {
        currentGLongitudinal = gEstimator.gLongitudinal
        currentGLateral = gEstimator.gLateral
        currentGTotal = hypot(currentGLateral, currentGLongitudinal)
        if (abs(currentGLateral) > maxGLateral) maxGLateral = abs(currentGLateral)
        if (currentGTotal > maxGTotal) maxGTotal = currentGTotal
    }

    private fun maybeRecordSample() {
        val active = recorder ?: return
        val nowMs = SystemClock.elapsedRealtime()
        // Throttle to ~10 Hz so we don't hammer disk I/O from the sensor callback.
        if (nowMs - lastRecordElapsedMs < 100L) return
        lastRecordElapsedMs = nowMs
        active.writeSample(
            elapsedMs = nowMs - sessionStartElapsedMs,
            lat = lastLocation?.latitude,
            lon = lastLocation?.longitude,
            accuracyM = lastLocation?.accuracy,
            speedKmh = currentSpeedKmh,
            gLat = currentGLateral,
            gLong = currentGLongitudinal,
            gTotal = currentGTotal,
            lap = lapTracker.lapCount,
            rpm = currentRpm,
            cvtRatio = gearing.cvtRatio(currentRpm, currentSpeedKmh),
            yawRateDps = gEstimator.yawRateDps,
            gCalibrated = gEstimator.isCalibrated,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        if (location.hasSpeed()) {
            gEstimator.onGpsFix(location.speed.toDouble(), location.elapsedRealtimeNanos)
            currentSpeedKmh = location.speed * 3.6
            if (currentSpeedKmh > maxSpeedKmh) maxSpeedKmh = currentSpeedKmh
        }
        if (location.hasBearing() && location.hasSpeed() && location.speed > 2.0f) {
            lastBearingDeg = location.bearing
        }
        recordTrackPoint(location)
        val lapTimeMs = lapTracker.onLocation(location, SystemClock.elapsedRealtime(), sessionStartElapsedMs)
        if (lapTimeMs != null) onLapCompleted(lapTimeMs)
        pushState()
    }

    private fun recordTrackPoint(location: Location) {
        val previous = trackPoints.lastOrNull()
        val distanceM = if (previous != null) {
            val results = FloatArray(1)
            Location.distanceBetween(previous.lat, previous.lon, location.latitude, location.longitude, results)
            results[0]
        } else {
            Float.MAX_VALUE
        }
        if (distanceM < MIN_TRACK_POINT_DISTANCE_M) return

        trackPoints.add(GeoPoint(location.latitude, location.longitude))
        if (trackPoints.size > MAX_TRACK_POINTS) {
            // Halve resolution instead of dropping the oldest points, so the
            // whole recorded circuit shape stays visible over a long session.
            val decimated = ArrayList<GeoPoint>(trackPoints.size / 2 + 1)
            for (i in trackPoints.indices step 2) decimated.add(trackPoints[i])
            trackPoints.clear()
            trackPoints.addAll(decimated)
        }
        trackSnapshot = trackPoints.toList()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun pushState() {
        val now = SystemClock.elapsedRealtime()
        onState(
            TelemetryUiState(
                gpsHasFix = lastLocation != null,
                gpsAccuracyM = lastLocation?.accuracy,
                speedKmh = currentSpeedKmh,
                maxSpeedKmh = maxSpeedKmh,
                gAvailable = gAvailable,
                gLateral = currentGLateral,
                gLongitudinal = currentGLongitudinal,
                gTotal = currentGTotal,
                maxGLateral = maxGLateral,
                maxGTotal = min(maxGTotal, 9.99),
                gCalibrated = gEstimator.isCalibrated,
                rpmAvailable = audioMonitor.isRunning,
                rpm = currentRpm,
                maxRpm = maxRpm,
                gearing = gearing,
                cvtRatio = gearing.cvtRatio(currentRpm, currentSpeedKmh),
                currentLapMs = now - lapTracker.currentLapReference(sessionStartElapsedMs),
                lastLapMs = lastLapMs,
                bestLapMs = bestLapMs,
                lapCount = lapTracker.lapCount,
                finishLineSet = lapTracker.finishLine != null,
                isRecording = recorder != null,
                lastRecordedFileName = recorder?.file?.name,
                trackPoints = trackSnapshot,
                currentPoint = lastLocation?.let { GeoPoint(it.latitude, it.longitude) },
                finishPoint = lapTracker.finishLine?.let { GeoPoint(it.latitude, it.longitude) },
            )
        )
    }

    private companion object {
        const val MIN_TRACK_POINT_DISTANCE_M = 2f
        const val MAX_TRACK_POINTS = 4000
    }
}
