package com.ander.karttelemetry.core

data class TelemetryUiState(
    val gpsHasFix: Boolean = false,
    val gpsAccuracyM: Float? = null,
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val gAvailable: Boolean = true,
    val gLateral: Double = 0.0,
    val gLongitudinal: Double = 0.0,
    val gTotal: Double = 0.0,
    val maxGLateral: Double = 0.0,
    val maxGTotal: Double = 0.0,
    val currentLapMs: Long = 0L,
    val lastLapMs: Long? = null,
    val bestLapMs: Long? = null,
    val lapCount: Int = 0,
    val finishLineSet: Boolean = false,
    val isRecording: Boolean = false,
    val lastRecordedFileName: String? = null,
    val trackPoints: List<GeoPoint> = emptyList(),
    val currentPoint: GeoPoint? = null,
    val finishPoint: GeoPoint? = null,
)
