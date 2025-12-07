package com.kaidwal.securefieldcommunicationsystem

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import timber.log.Timber
import java.util.concurrent.LinkedBlockingQueue

class AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var isSpeakerMode = true
    private var isPlaying = false

    // Crypto engine for decryption
    private val cryptoEngine = CryptoEngine()

    // Audio buffer queue for smooth playback
    private val audioQueue = LinkedBlockingQueue<ByteArray>(50)
    private var playbackThread: Thread? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormatEncoding = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormatEncoding)

    init {
        initAudioTrack()
        startPlaybackThread()
    }

    private fun initAudioTrack() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(
                    if (isSpeakerMode)
                        AudioAttributes.USAGE_MEDIA
                    else
                        AudioAttributes.USAGE_VOICE_COMMUNICATION
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormatBuilder = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormatEncoding)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormatBuilder,
                bufferSize * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
                Timber.d("Audio player initialized - Speaker: $isSpeakerMode")
            } else {
                Timber.e("AudioTrack initialization failed")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize audio player")
        }
    }

    private fun startPlaybackThread() {
        isPlaying = true

        playbackThread = Thread({
            Timber.d("Playback thread started")

            while (isPlaying) {
                try {
                    val audioData = audioQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)

                    if (audioData != null) {
                        val written = audioTrack?.write(audioData, 0, audioData.size) ?: 0

                        if (written < 0) {
                            Timber.e("Error writing audio: $written")
                        } else if (written < audioData.size) {
                            Timber.w("Partial write: $written/${audioData.size} bytes")
                        }
                    }
                } catch (e: InterruptedException) {
                    Timber.d("Playback thread interrupted")
                    break
                } catch (e: Exception) {
                    Timber.e(e, "Error in playback thread")
                }
            }

            Timber.d("Playback thread ended")
        }, "AudioPlaybackThread")

        playbackThread?.start()
    }

    /**
     * Play audio data (decrypts encrypted audio from network)
     */
    fun playAudio(encryptedAudioData: ByteArray) {
        try {
            // Decrypt audio data using CryptoEngine
            val decryptedAudio = cryptoEngine.decrypt(encryptedAudioData)

            // Add to playback queue
            if (!audioQueue.offer(decryptedAudio)) {
                Timber.w("Audio queue full, dropping packet")
                audioQueue.poll()
                audioQueue.offer(decryptedAudio)
            }

            Timber.v("Decrypted and queued ${decryptedAudio.size} bytes for playback")

        } catch (e: Exception) {
            Timber.e(e, "Failed to play audio")
        }
    }

    /**
     * Set crypto engine session key
     */
    fun setSessionKey(key: ByteArray) {
        cryptoEngine.setSessionKey(key)
        Timber.d("AudioPlayer session key updated")
    }

    fun setSpeakerMode(enabled: Boolean) {
        if (isSpeakerMode == enabled) return

        isSpeakerMode = enabled
        Timber.d("Speaker mode changed to: $enabled")

        audioQueue.clear()
        release()
        initAudioTrack()
        startPlaybackThread()
    }

    fun clearBuffer() {
        audioQueue.clear()
        Timber.d("Audio buffer cleared")
    }

    fun getBufferSize(): Int = audioQueue.size

    fun isReady(): Boolean {
        return audioTrack?.state == AudioTrack.STATE_INITIALIZED && isPlaying
    }

    fun release() {
        isPlaying = false

        try {
            playbackThread?.interrupt()
            playbackThread?.join(1000)
        } catch (e: InterruptedException) {
            Timber.w(e, "Playback thread interrupted during shutdown")
        }

        audioQueue.clear()

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        playbackThread = null

        Timber.d("AudioPlayer released")
    }
}
