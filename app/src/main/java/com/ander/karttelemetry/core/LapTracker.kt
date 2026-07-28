package com.ander.karttelemetry.core

import android.location.Location

/**
 * Detects lap crossings by proximity to a saved "finish line" GPS point.
 * This is a radius check, not a true line-crossing test, so accuracy is
 * bounded by phone GPS precision (typically a few meters). A re-arm
 * distance is required between crossings so a single pass through the
 * trigger radius can't be counted twice.
 */
class LapTracker {

    var finishLine: Location? = null
        private set

    private var armed = true

    var lastCrossingElapsedMs: Long = 0L
        private set

    val lapTimes = mutableListOf<Long>()
    val lapCount get() = lapTimes.size

    fun setFinishLine(location: Location) {
        finishLine = location
        armed = true
    }

    fun currentLapReference(sessionStartElapsedMs: Long): Long =
        if (lastCrossingElapsedMs == 0L) sessionStartElapsedMs else lastCrossingElapsedMs

    /** Returns the completed lap time in ms if this location just triggered a valid crossing. */
    fun onLocation(location: Location, nowElapsedMs: Long, sessionStartElapsedMs: Long): Long? {
        val finish = finishLine ?: return null
        val distanceM = location.distanceTo(finish)

        if (armed && distanceM < TRIGGER_RADIUS_M) {
            armed = false
            val reference = currentLapReference(sessionStartElapsedMs)
            val lapTimeMs = nowElapsedMs - reference
            if (lapTimeMs >= MIN_LAP_MS) {
                lapTimes.add(lapTimeMs)
                lastCrossingElapsedMs = nowElapsedMs
                return lapTimeMs
            }
            return null
        } else if (!armed && distanceM > REARM_RADIUS_M) {
            armed = true
        }
        return null
    }

    fun manualLap(nowElapsedMs: Long, sessionStartElapsedMs: Long): Long {
        val reference = currentLapReference(sessionStartElapsedMs)
        val lapTimeMs = nowElapsedMs - reference
        lapTimes.add(lapTimeMs)
        lastCrossingElapsedMs = nowElapsedMs
        return lapTimeMs
    }

    fun reset() {
        lapTimes.clear()
        lastCrossingElapsedMs = 0L
        armed = true
    }

    companion object {
        const val TRIGGER_RADIUS_M = 15f
        const val REARM_RADIUS_M = 40f
        const val MIN_LAP_MS = 5000L
    }
}
