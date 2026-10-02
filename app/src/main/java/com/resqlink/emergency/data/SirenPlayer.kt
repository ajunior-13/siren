package com.resqlink.emergency.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*

/**
 * Local loud siren that plays on the device that triggered SOS.
 * (The remote person receives an SMS alert on their phone.)
 */
class SirenPlayer {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var job: Job? = null

    fun startSiren() {
        if (isPlaying) return
        isPlaying = true

        job = CoroutineScope(Dispatchers.IO).launch {
            val sampleRate = 44100
            val chunkSamples = 2000

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(chunkSamples * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            var freq = 600.0
            var rising = true
            var phase = 0.0

            while (isPlaying && isActive) {
                val buffer = ByteArray(chunkSamples * 2)
                for (i in 0 until chunkSamples) {
                    if (rising) {
                        freq += 0.8
                        if (freq >= 1400.0) rising = false
                    } else {
                        freq -= 0.8
                        if (freq <= 600.0) rising = true
                    }
                    phase += 2.0 * Math.PI * freq / sampleRate
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                    val sample = (Math.sin(phase) * 28000).toInt().toShort()
                    buffer[i * 2] = (sample.toInt() and 0xff).toByte()
                    buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xff).toByte()
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stopSiren() {
        isPlaying = false
        job?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    fun isPlaying(): Boolean = isPlaying
}
