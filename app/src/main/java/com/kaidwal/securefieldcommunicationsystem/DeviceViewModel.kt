package com.kaidwal.securefieldcommunicationsystem

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DeviceDatabase.getInstance(application)
    private val bluetoothManager = BluetoothManager(application)
    private val wifiDirectManager = WiFiDirectManager(application)
    private val cryptoEngine = CryptoEngine()

    private var bluetoothServerMode: BluetoothServerMode? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isServerMode = MutableStateFlow(false)
    val isServerMode: StateFlow<Boolean> = _isServerMode.asStateFlow()

    private val _connectionState = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Idle)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _meshNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val meshNodes: StateFlow<List<MeshNode>> = _meshNodes.asStateFlow()

    data class DiscoveredDevice(
        val deviceId: String,
        val displayName: String,
        val publicKey: String,
        val connectionType: String,
        val signalStrength: Int,
        val lastSeen: Long,
        val isConnected: Boolean = false,
        val bluetoothDevice: BluetoothDevice? = null
    )

    data class MeshNode(
        val deviceId: String,
        val isActive: Boolean,
        val connections: List<String>
    )

    sealed class DeviceConnectionState {
        object Idle : DeviceConnectionState()
        object Scanning : DeviceConnectionState()
        data class Connecting(val deviceId: String) : DeviceConnectionState()
        data class Connected(val deviceId: String) : DeviceConnectionState()
        data class Error(val message: String) : DeviceConnectionState()
    }

    init {
        loadSavedDevices()
        observeBluetoothDevices()
        observeWiFiDirectPeers()
    }

    fun startServerMode() {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val serviceIntent = Intent(app, CommunicationService::class.java)
                app.startService(serviceIntent)

                delay(500L)

                bluetoothServerMode = BluetoothServerMode(bluetoothManager) { socket ->
                    Timber.d("Server accepted connection from: ${socket.remoteDevice.address}")
                    handleIncomingConnection(socket)
                }
                bluetoothServerMode?.startServer()
                _isServerMode.value = true
                Timber.d("Server mode started - listening for connections")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start server mode")
                _isServerMode.value = false
            }
        }
    }

    fun stopServerMode() {
        bluetoothServerMode?.stopServer()
        bluetoothServerMode = null
        _isServerMode.value = false
        Timber.d("Server mode stopped")
    }

    private fun handleIncomingConnection(socket: android.bluetooth.BluetoothSocket) {
        viewModelScope.launch {
            try {
                val deviceAddress = socket.remoteDevice.address
                val deviceName = socket.remoteDevice.name ?: "Unknown Device"

                database.deviceDao().insertDevice(
                    DeviceEntity(
                        deviceId = deviceAddress,
                        publicKey = cryptoEngine.generateKeyPair().publicKey.toHex(),
                        displayName = deviceName,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = true,
                        connectionType = "BLUETOOTH"
                    )
                )

                ConnectionSocketManager.setAcceptedSocket(deviceAddress, socket)

                _connectionState.value = DeviceConnectionState.Connected(deviceAddress)

                loadSavedDevices()

                Timber.d("Incoming connection saved: $deviceAddress")
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle incoming connection")
            }
        }
    }

    fun startDiscovery(transport: TransportMode) {
        Timber.d("Starting discovery with transport: $transport")

        when (transport) {
            TransportMode.BLUETOOTH -> {
                startScanning()
            }
            TransportMode.WIFI_DIRECT -> {
                startScanning()
            }
            TransportMode.RADIO_FM -> {
                Timber.w("Radio FM not yet implemented")
            }
        }
    }

    fun startScanning() {
        viewModelScope.launch {
            try {
                _isScanning.value = true
                _connectionState.value = DeviceConnectionState.Scanning

                if (!bluetoothManager.hasPermissions()) {
                    _connectionState.value = DeviceConnectionState.Error("Permissions not granted")
                    _isScanning.value = false
                    return@launch
                }

                if (bluetoothManager.isBluetoothEnabled()) {
                    bluetoothManager.startDiscovery()
                    val pairedDevices = bluetoothManager.getPairedDevices()
                    updateDiscoveredDevices(pairedDevices)
                }

                wifiDirectManager.discoverPeers()

                Timber.d("Started real device scanning")
            } catch (e: Exception) {
                Timber.e(e, "Scanning failed")
                _connectionState.value = DeviceConnectionState.Error(e.message ?: "Scanning failed")
                _isScanning.value = false
            }
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            try {
                _isScanning.value = false
                _connectionState.value = DeviceConnectionState.Idle

                bluetoothManager.stopDiscovery()
                wifiDirectManager.stopPeerDiscovery()

                Timber.d("Stopped device scanning")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop scanning")
            }
        }
    }

    private fun observeBluetoothDevices() {
        viewModelScope.launch {
            bluetoothManager.discoveredDevices.collect { devices ->
                updateDiscoveredDevices(devices.toSet())
            }
        }
    }

    private fun observeWiFiDirectPeers() {
        viewModelScope.launch {
            wifiDirectManager.peers.collect { peers ->
                val wifiDevices = peers.map { peer ->
                    DiscoveredDevice(
                        deviceId = peer.deviceAddress,
                        displayName = peer.deviceName ?: "WiFi Device",
                        publicKey = cryptoEngine.generateKeyPair().publicKey.toHex(),
                        connectionType = "WIFI_DIRECT",
                        signalStrength = 85,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = false
                    )
                }

                val currentDevices = _discoveredDevices.value.filter { it.connectionType == "BLUETOOTH" }
                _discoveredDevices.value = currentDevices + wifiDevices
            }
        }
    }

    private fun updateDiscoveredDevices(bluetoothDevices: Set<BluetoothDevice>) {
        val deviceList = bluetoothDevices.map { device ->
            DiscoveredDevice(
                deviceId = device.address,
                displayName = device.name ?: "Unknown Device",
                publicKey = cryptoEngine.generateKeyPair().publicKey.toHex(),
                connectionType = "BLUETOOTH",
                signalStrength = 80,
                lastSeen = System.currentTimeMillis(),
                isConnected = false,
                bluetoothDevice = device
            )
        }

        val wifiDevices = _discoveredDevices.value.filter { it.connectionType == "WIFI_DIRECT" }
        _discoveredDevices.value = deviceList + wifiDevices

        Timber.d("Updated device list: ${deviceList.size} Bluetooth, ${wifiDevices.size} WiFi")
    }

    fun connectToDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                _connectionState.value = DeviceConnectionState.Connecting(deviceId)

                val device = _discoveredDevices.value.find { it.deviceId == deviceId }

                if (device == null) {
                    _connectionState.value = DeviceConnectionState.Error("Device not found")
                    return@launch
                }

                when (device.connectionType) {
                    "BLUETOOTH" -> {
                        device.bluetoothDevice?.let { btDevice ->
                            val socket = bluetoothManager.connectToDevice(btDevice)
                            if (socket != null) {
                                database.deviceDao().insertDevice(
                                    DeviceEntity(
                                        deviceId = device.deviceId,
                                        publicKey = device.publicKey,
                                        displayName = device.displayName,
                                        lastSeen = System.currentTimeMillis(),
                                        isConnected = true,
                                        connectionType = device.connectionType
                                    )
                                )

                                _connectionState.value = DeviceConnectionState.Connected(deviceId)
                                updateDeviceConnectionStatus(deviceId, true)
                                loadSavedDevices()

                                Timber.d("Successfully connected to Bluetooth device: $deviceId")
                            } else {
                                _connectionState.value = DeviceConnectionState.Error("Bluetooth connection failed")
                            }
                        }
                    }

                    "WIFI_DIRECT" -> {
                        val peer = wifiDirectManager.peers.value.find { it.deviceAddress == deviceId }
                        if (peer != null) {
                            wifiDirectManager.connectToPeer(peer)

                            database.deviceDao().insertDevice(
                                DeviceEntity(
                                    deviceId = device.deviceId,
                                    publicKey = device.publicKey,
                                    displayName = device.displayName,
                                    lastSeen = System.currentTimeMillis(),
                                    isConnected = true,
                                    connectionType = device.connectionType
                                )
                            )

                            _connectionState.value = DeviceConnectionState.Connected(deviceId)
                            updateDeviceConnectionStatus(deviceId, true)
                            loadSavedDevices()

                            Timber.d("Successfully connected to WiFi Direct device: $deviceId")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection failed")
                _connectionState.value = DeviceConnectionState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                database.deviceDao().updateConnectionStatus(deviceId, false)
                updateDeviceConnectionStatus(deviceId, false)

                _connectionState.value = DeviceConnectionState.Idle

                Timber.d("Disconnected from device: $deviceId")
            } catch (e: Exception) {
                Timber.e(e, "Disconnect failed")
            }
        }
    }

    private fun loadSavedDevices() {
        viewModelScope.launch {
            try {
                val devices = database.deviceDao().getAllDevices()
                val discoveredDevices = devices.map { entity ->
                    DiscoveredDevice(
                        deviceId = entity.deviceId,
                        displayName = entity.displayName ?: "Unknown Device",
                        publicKey = entity.publicKey,
                        connectionType = entity.connectionType,
                        signalStrength = 75,
                        lastSeen = entity.lastSeen,
                        isConnected = entity.isConnected
                    )
                }

                _discoveredDevices.value = discoveredDevices

                Timber.d("Loaded ${devices.size} saved devices")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load saved devices")
            }
        }
    }

    private fun updateDeviceConnectionStatus(deviceId: String, isConnected: Boolean) {
        val updatedDevices = _discoveredDevices.value.map { device ->
            if (device.deviceId == deviceId) {
                device.copy(isConnected = isConnected)
            } else {
                device
            }
        }
        _discoveredDevices.value = updatedDevices
    }

    fun getDeviceStats(): Map<String, Int> {
        val devices = _discoveredDevices.value
        return mapOf(
            "total" to devices.size,
            "connected" to devices.count { it.isConnected },
            "bluetooth" to devices.count { it.connectionType == "BLUETOOTH" },
            "wifiDirect" to devices.count { it.connectionType == "WIFI_DIRECT" }
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopServerMode()
        bluetoothManager.cleanup()
        wifiDirectManager.cleanup()
    }
}

private fun ByteArray.toHex(): String {
    return this.joinToString("") { "%02x".format(it) }
}

object ConnectionSocketManager {
    private val sockets = mutableMapOf<String, android.bluetooth.BluetoothSocket>()

    fun setAcceptedSocket(deviceId: String, socket: android.bluetooth.BluetoothSocket) {
        sockets[deviceId] = socket
        Timber.d("Stored accepted socket for: $deviceId")
    }

    fun getSocket(deviceId: String): android.bluetooth.BluetoothSocket? {
        return sockets[deviceId]
    }

    fun clearSocket(deviceId: String) {
        sockets.remove(deviceId)
    }
}
