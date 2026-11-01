package com.ander.karttelemetry.core

import android.content.Context
import java.io.File

/** Reads back the CSV files written by [SessionRecorder] for the session review screens. */
object SessionFileReader {

    fun listSessions(context: Context): List<File> {
        val dir = File(context.getExternalFilesDir(null), "sessions")
        val files = dir.listFiles { f -> f.isFile && f.extension == "csv" } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    // Column order matches SessionRecorder.writeSample:
    // timestamp,elapsed_ms,lat,lon,accuracy_m,speed_kmh,g_lateral,g_longitudinal,g_total,lap,rpm,cvt_ratio
    // (rpm was added later; older files simply don't have it.)
    fun readSamples(file: File): List<SessionSample> {
        val samples = mutableListOf<SessionSample>()
        file.forEachLine { line ->
            if (line.startsWith("timestamp,")) return@forEachLine
            val cols = line.split(",")
            if (cols.size < 10) return@forEachLine
            val elapsedMs = cols[1].toLongOrNull() ?: return@forEachLine
            val speedKmh = cols[5].toDoubleOrNull() ?: return@forEachLine
            val gLateral = cols[6].toDoubleOrNull() ?: return@forEachLine
            val gLongitudinal = cols[7].toDoubleOrNull() ?: return@forEachLine
            val lap = cols[9].toIntOrNull() ?: 0
            val rpm = cols.getOrNull(10)?.toDoubleOrNull()
            samples.add(SessionSample(elapsedMs, speedKmh, gLateral, gLongitudinal, lap, rpm))
        }
        return samples
    }
}
