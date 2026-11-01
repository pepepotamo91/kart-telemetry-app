package com.ander.karttelemetry.core

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes one CSV row per recorded sample to a session file for later analysis. */
class SessionRecorder(val file: File) {

    private val writer: BufferedWriter = BufferedWriter(FileWriter(file))
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    init {
        writer.write("timestamp,elapsed_ms,lat,lon,accuracy_m,speed_kmh,g_lateral,g_longitudinal,g_total,lap,rpm,cvt_ratio")
        writer.newLine()
    }

    fun writeSample(
        elapsedMs: Long,
        lat: Double?,
        lon: Double?,
        accuracyM: Float?,
        speedKmh: Double,
        gLat: Double,
        gLong: Double,
        gTotal: Double,
        lap: Int,
        rpm: Double?,
        cvtRatio: Double?,
    ) {
        val row = listOf(
            timeFormat.format(Date()),
            elapsedMs.toString(),
            lat?.toString() ?: "",
            lon?.toString() ?: "",
            accuracyM?.toString() ?: "",
            "%.2f".format(Locale.US, speedKmh),
            "%.3f".format(Locale.US, gLat),
            "%.3f".format(Locale.US, gLong),
            "%.3f".format(Locale.US, gTotal),
            lap.toString(),
            rpm?.let { "%.0f".format(Locale.US, it) } ?: "",
            cvtRatio?.let { "%.3f".format(Locale.US, it) } ?: "",
        ).joinToString(",")
        writer.write(row)
        writer.newLine()
    }

    fun close() {
        runCatching {
            writer.flush()
            writer.close()
        }
    }
}
