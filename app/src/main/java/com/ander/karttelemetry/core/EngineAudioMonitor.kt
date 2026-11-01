package com.ander.karttelemetry.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat

/**
 * Listens to the microphone on a background thread and reports engine RPM
 * (via [RpmEstimator]) on the main thread about 10 times per second.
 * [onRpm] receives null while there is no confident reading.
 */
class EngineAudioMonitor(
    private val context: Context,
    private val onRpm: (Double?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts listening if the mic permission is granted. Returns whether it is running. */
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) return false
        val record = openAudioRecord() ?: return false
        running = true
        thread = Thread({ loop(record) }, "engine-rpm").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop(record: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val inputRate = record.sampleRate
        val decimator = Decimator(inputRate)
        val estimator = RpmEstimator(decimator.outputRate)
        val hopIn = inputRate / ANALYSES_PER_SECOND
        val pcm = ShortArray(hopIn)
        val decimated = DoubleArray(hopIn / decimator.factor + 2)

        try {
            record.startRecording()
            while (running) {
                var got = 0
                while (got < hopIn && running) {
                    val n = record.read(pcm, got, hopIn - got)
                    if (n <= 0) break
                    got += n
                }
                if (got <= 0) continue
                val n = decimator.process(pcm, got, decimated)
                estimator.push(decimated, n)
                val rpm = estimator.analyze()?.rpm
                mainHandler.post { if (running) onRpm(rpm) }
            }
        } catch (_: Exception) {
            // Mic taken by another app or revoked: just stop reporting.
        } finally {
            runCatching { record.stop() }
            record.release()
            mainHandler.post { onRpm(null) }
        }
    }

    @SuppressLint("MissingPermission") // checked in start()
    private fun openAudioRecord(): AudioRecord? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sources = buildList {
            // UNPROCESSED skips noise suppression / AGC, which would eat the engine
            // harmonics; VOICE_RECOGNITION is the next least-processed source.
            if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
        for (source in sources) {
            for (rate in intArrayOf(8000, 16000, 22050, 44100, 48000)) {
                val minBuf = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) continue
                val record = runCatching {
                    AudioRecord(
                        source, rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate / 2 * 2),
                    )
                }.getOrNull() ?: continue
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
            }
        }
        return null
    }

    private companion object {
        const val ANALYSES_PER_SECOND = 10
    }
}
