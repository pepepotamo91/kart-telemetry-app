package com.ander.karttelemetry.core

data class SessionSample(
    val elapsedMs: Long,
    val speedKmh: Double,
    val gLateral: Double,
    val gLongitudinal: Double,
    val lap: Int,
)
