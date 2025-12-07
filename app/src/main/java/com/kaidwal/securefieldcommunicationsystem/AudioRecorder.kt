package com.kaidwal.securefieldcommunicationsystem

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import timber.log.Timber
import kotlin.concurrent.thread

class AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // Callback for captured audio data
    var onAudioDataCaptured: ((ByteArray) -> Unit)? = null

    private val sampleRate = 16000 // 16kHz for voice
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun startRecording() {
        try {
            if (isRecording) {
                Timber.w("Already recording")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            Timber.d("Audio recording started")

            // Start recording thread to capture audio data
            recordingThread = thread(start = true, name = "AudioRecorderThread") {
                val audioBuffer = ByteArray(bufferSize)

                while (isRecording) {
                    try {
                        val read = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0

                        if (read > 0) {
                            // Create a copy of the buffer for this chunk
                            val audioChunk = audioBuffer.copyOf(read)

                            // Send to callback for encryption & transmission
                            onAudioDataCaptured?.invoke(audioChunk)

                            Timber.v("Captured ${audioChunk.size} bytes of audio")
                        } else if (read < 0) {
                            Timber.e("Error reading audio: $read")
                            break
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error during recording")
                        break
                    }
                }

                Timber.d("Recording thread ended")
            }

        } catch (e: SecurityException) {
            Timber.e(e, "Microphone permission denied")
            isRecording = false
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            isRecording = false
        }
    }

    fun stopRecording() {
        isRecording = false

        try {
            recordingThread?.join(1000) // Wait max 1 second for thread to finish
        } catch (e: InterruptedException) {
            Timber.w(e, "Recording thread interrupted")
        }

        audioRecord?.stop()
        Timber.d("Audio recording stopped")
    }

    fun release() {
        stopRecording()
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        Timber.d("AudioRecorder released")
    }

    /**
     * Set the callback for captured audio data
     * The callback will receive encrypted audio chunks ready for transmission
     */
    fun setAudioDataCallback(callback: (ByteArray) -> Unit) {
        onAudioDataCaptured = callback
    }

    /**
     * Get current recording state
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Get audio configuration info
     */
    fun getAudioConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = sampleRate,
            channelConfig = channelConfig,
            audioFormat = audioFormat,
            bufferSize = bufferSize
        )
    }

    data class AudioConfig(
        val sampleRate: Int,
        val channelConfig: Int,
        val audioFormat: Int,
        val bufferSize: Int
    )
}
