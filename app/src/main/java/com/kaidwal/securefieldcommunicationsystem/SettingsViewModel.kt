package com.kaidwal.securefieldcommunicationsystem

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Settings ViewModel
 * Manages app settings and preferences
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DeviceDatabase.getInstance(application)
    private val cryptoEngine = CryptoEngine()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _encryptionEnabled = MutableStateFlow(true)
    val encryptionEnabled: StateFlow<Boolean> = _encryptionEnabled.asStateFlow()

    private val _autoDeleteEnabled = MutableStateFlow(true)
    val autoDeleteEnabled: StateFlow<Boolean> = _autoDeleteEnabled.asStateFlow()

    private val _meshNetworkEnabled = MutableStateFlow(true)
    val meshNetworkEnabled: StateFlow<Boolean> = _meshNetworkEnabled.asStateFlow()

    private val _connectionType = MutableStateFlow("Auto")
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    // Voice settings
    private val _highQualityAudio = MutableStateFlow(false)
    val highQualityAudio: StateFlow<Boolean> = _highQualityAudio.asStateFlow()

    private val _noiseSuppression = MutableStateFlow(true)
    val noiseSuppression: StateFlow<Boolean> = _noiseSuppression.asStateFlow()

    private val _autoGainControl = MutableStateFlow(true)
    val autoGainControl: StateFlow<Boolean> = _autoGainControl.asStateFlow()

    private val _pttSensitivity = MutableStateFlow(0.5f)
    val pttSensitivity: StateFlow<Float> = _pttSensitivity.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _deviceId.value = cryptoEngine.generateDeviceId()
        }
    }

    fun toggleEncryption(enabled: Boolean) {
        _encryptionEnabled.value = enabled
        Timber.d("Encryption ${if (enabled) "enabled" else "disabled"}")
    }

    fun toggleAutoDelete(enabled: Boolean) {
        _autoDeleteEnabled.value = enabled
        Timber.d("Auto-delete ${if (enabled) "enabled" else "disabled"}")
    }

    fun toggleMeshNetwork(enabled: Boolean) {
        _meshNetworkEnabled.value = enabled
        Timber.d("Mesh network ${if (enabled) "enabled" else "disabled"}")
    }

    fun setConnectionType(type: String) {
        _connectionType.value = type
        Timber.d("Connection type set to: $type")
    }

    fun rotateKeys() {
        viewModelScope.launch {
            val newDeviceId = cryptoEngine.generateDeviceId()
            _deviceId.value = newDeviceId
            Timber.d("Keys rotated, new device ID: $newDeviceId")
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            database.messageDao().deleteAllMessages()
            Timber.d("All messages cleared")
        }
    }

    fun clearDevices() {
        viewModelScope.launch {
            database.deviceDao().deleteAllDevices()
            Timber.d("All devices cleared")
        }
    }

    fun setHighQualityAudio(enabled: Boolean) {
        _highQualityAudio.value = enabled
        Timber.d("High quality audio: $enabled")
    }

    fun setNoiseSuppression(enabled: Boolean) {
        _noiseSuppression.value = enabled
        Timber.d("Noise suppression: $enabled")
    }

    fun setAutoGainControl(enabled: Boolean) {
        _autoGainControl.value = enabled
        Timber.d("Auto-gain control: $enabled")
    }

    fun setPTTSensitivity(value: Float) {
        _pttSensitivity.value = value
        Timber.d("PTT sensitivity: $value")
    }
}
