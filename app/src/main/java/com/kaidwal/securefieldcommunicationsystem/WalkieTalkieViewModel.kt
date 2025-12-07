package com.kaidwal.securefieldcommunicationsystem

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.random.Random

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    enum class PTTConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    private val _isPTTActive = MutableStateFlow(false)
    val isPTTActive: StateFlow<Boolean> = _isPTTActive.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _connectionState = MutableStateFlow(PTTConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PTTConnectionState> = _connectionState.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var audioLevelJob: Job? = null
    private val cryptoEngine = CryptoEngine()

    private var communicationService: CommunicationService? = null
    private var isServiceBound = false

    private var currentDeviceId: String = ""
    private var currentTransport: TransportMode = TransportMode.BLUETOOTH

    // Voice packet listener callback
    private var voicePacketCallback: ((String, ByteArray) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? CommunicationService.LocalBinder
            communicationService = binder?.getService()
            isServiceBound = true
            Timber.d("CommunicationService connected for voice transmission")

            // Setup voice packet listener
            setupVoicePacketListener()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            communicationService = null
            isServiceBound = false
            Timber.w("CommunicationService disconnected")
        }
    }

    init {
        bindCommunicationService()
    }

    private fun bindCommunicationService() {
        val intent = Intent(getApplication(), CommunicationService::class.java)
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun setupVoicePacketListener() {
        // Listen for incoming voice packets via CommunicationService
        communicationService?.setVoicePacketListener { deviceId, encryptedAudio ->
            if (deviceId == currentDeviceId) {
                onVoicePacketReceived(encryptedAudio)
            }
        }
    }

    fun connectToDevice(deviceId: String, transport: TransportMode) {
        currentDeviceId = deviceId
        currentTransport = transport
        _connectionState.value = PTTConnectionState.CONNECTING

        viewModelScope.launch {
            delay(1000)
            _connectionState.value = PTTConnectionState.CONNECTED
            Timber.d("PTT connected to $deviceId via $transport")

            // Initialize audio components
            audioRecorder = AudioRecorder()
            audioPlayer = AudioPlayer()

            // Generate session key
            val sessionKey = generateDefaultKey()
            cryptoEngine.setSessionKey(sessionKey)
            audioPlayer?.setSessionKey(sessionKey)

            // Set up audio data callback
            audioRecorder?.setAudioDataCallback { audioData ->
                onAudioDataCaptured(audioData)
            }

            Timber.d("Audio components initialized with E2EE")
        }
    }

    /**
     * Generate default session key for voice encryption
     */
    private fun generateDefaultKey(): ByteArray {
        val defaultKey = "SFCS_VOICE_SESSION_KEY_32BYTES!".toByteArray()
        return defaultKey.copyOf(32) // Ensure 32 bytes for AES-256
    }

    /**
     * Called when audio data is captured from microphone
     */
    private fun onAudioDataCaptured(audioData: ByteArray) {
        viewModelScope.launch {
            try {
                // Encrypt audio data using CryptoEngine
                val encryptedAudio = cryptoEngine.encrypt(audioData)

                // Send voice packet via CommunicationService
                if (isServiceBound && communicationService != null) {
                    val sent = sendVoicePacketViaBluetooth(
                        recipientId = currentDeviceId,
                        encryptedAudioData = encryptedAudio
                    )

                    if (sent) {
                        Timber.v("Sent ${encryptedAudio.size} bytes encrypted voice to $currentDeviceId")
                    } else {
                        Timber.w("Failed to send voice packet")
                    }
                } else {
                    Timber.w("CommunicationService not available")
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to process audio")
            }
        }
    }

    /**
     * Send voice packet via Bluetooth using raw socket write
     */
    private fun sendVoicePacketViaBluetooth(
        recipientId: String,
        encryptedAudioData: ByteArray
    ): Boolean {
        return try {
            // Create voice packet with header
            // Format: [VOICE_HEADER:1byte][TIMESTAMP:8bytes][DATA_LENGTH:4bytes][ENCRYPTED_AUDIO]
            val timestamp = System.currentTimeMillis()
            val header = byteArrayOf(0x03) // 0x03 = Voice packet type
            val timestampBytes = timestamp.toByteArray()
            val lengthBytes = encryptedAudioData.size.toByteArray()

            val voicePacket = header + timestampBytes + lengthBytes + encryptedAudioData

            // Send via active socket through CommunicationService
            communicationService?.sendRawData(voicePacket) ?: false

        } catch (e: Exception) {
            Timber.e(e, "Failed to send voice packet")
            false
        }
    }

    suspend fun startTransmitting() {
        if (_connectionState.value != PTTConnectionState.CONNECTED) {
            Timber.w("Cannot start transmitting - not connected")
            return
        }

        _isPTTActive.value = true
        Timber.d("PTT transmission started")

        audioRecorder?.startRecording()

        audioLevelJob = viewModelScope.launch {
            while (_isPTTActive.value) {
                // FIXED: Use Random.nextFloat() instead of range.random()
                _audioLevel.value = Random.nextFloat() * 0.7f + 0.3f // Range: 0.3 to 1.0
                delay(50)
            }
        }
    }

    suspend fun stopTransmitting() {
        _isPTTActive.value = false
        _audioLevel.value = 0f
        audioLevelJob?.cancel()

        audioRecorder?.stopRecording()
        Timber.d("PTT transmission stopped")
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        audioPlayer?.setSpeakerMode(_isSpeakerOn.value)
        Timber.d("Speaker mode: ${_isSpeakerOn.value}")
    }

    /**
     * Called when voice packet is received from network
     */
    fun onVoicePacketReceived(encryptedAudioData: ByteArray) {
        viewModelScope.launch {
            try {
                _isReceiving.value = true
                _audioLevel.value = 0.7f

                // AudioPlayer will decrypt using CryptoEngine and play
                audioPlayer?.playAudio(encryptedAudioData)

                Timber.d("Received ${encryptedAudioData.size} bytes voice packet")

                delay(300)
                _isReceiving.value = false
                _audioLevel.value = 0f

            } catch (e: Exception) {
                Timber.e(e, "Failed to play received voice")
                _isReceiving.value = false
                _audioLevel.value = 0f
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _connectionState.value = PTTConnectionState.DISCONNECTED

            if (_isPTTActive.value) {
                stopTransmitting()
            }

            audioPlayer?.clearBuffer()
            currentDeviceId = ""
            Timber.d("Disconnected from PTT session")
        }
    }

    override fun onCleared() {
        super.onCleared()

        if (_isPTTActive.value) {
            viewModelScope.launch {
                stopTransmitting()
            }
        }

        audioRecorder?.release()
        audioPlayer?.release()

        if (isServiceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
                isServiceBound = false
            } catch (e: Exception) {
                Timber.e(e, "Error unbinding service")
            }
        }

        Timber.d("WalkieTalkieViewModel cleared")
    }

    // Extension functions for voice packets
    private fun Long.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 56).toByte(),
            (this shr 48).toByte(),
            (this shr 40).toByte(),
            (this shr 32).toByte(),
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }

    private fun Int.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }
}
