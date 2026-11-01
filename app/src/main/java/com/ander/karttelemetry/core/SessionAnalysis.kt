package com.ander.karttelemetry.core

data class SessionPoint(
    val distanceM: Double,
    val speedKmh: Double,
    val gLateral: Double,
    val gLongitudinal: Double,
    val rpm: Double? = null,
)

data class SessionAnalysis(
    val points: List<SessionPoint>,
    val lapEndDistancesM: List<Double>,
)

/**
 * Turns raw time-based samples into a distance-based series for the review
 * charts. Distance is derived by integrating the recorded speed over time
 * rather than from lat/lon, since consecutive samples often share the same
 * GPS fix (location updates arrive slower than the recording rate).
 */
fun analyzeSession(samples: List<SessionSample>): SessionAnalysis {
    if (samples.isEmpty()) return SessionAnalysis(emptyList(), emptyList())

    val points = ArrayList<SessionPoint>(samples.size)
    val lapEnds = mutableListOf<Double>()
    var distanceM = 0.0
    var previous = samples[0]
    points.add(SessionPoint(0.0, previous.speedKmh, previous.gLateral, previous.gLongitudinal, previous.rpm))

    for (i in 1 until samples.size) {
        val current = samples[i]
        val dtSeconds = (current.elapsedMs - previous.elapsedMs).coerceAtLeast(0L) / 1000.0
        val avgSpeedMs = (previous.speedKmh + current.speedKmh) / 2.0 / 3.6
        distanceM += avgSpeedMs * dtSeconds
        points.add(SessionPoint(distanceM, current.speedKmh, current.gLateral, current.gLongitudinal, current.rpm))
        if (current.lap != previous.lap) lapEnds.add(distanceM)
        previous = current
    }
    return SessionAnalysis(points, lapEnds)
}
