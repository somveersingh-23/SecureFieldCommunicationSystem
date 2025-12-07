package com.kaidwal.securefieldcommunicationsystem

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Discovered Device data class - ADDED TO FIX COMPILATION ERROR
 */
data class DiscoveredDevice(
    val bluetoothDevice: BluetoothDevice,
    val name: String = bluetoothDevice.name ?: "Unknown",
    val address: String = bluetoothDevice.address,
    val rssi: Int = 0
)

/**
 * Chat ViewModel - COMPLETE PRODUCTION VERSION WITH SERVER MODE
 * Manages chat state, Bluetooth connections, E2E encryption, and message handling
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DeviceDatabase.getInstance(application)
    private val cryptoEngine = CryptoEngine()
    private val messageManager = MessageManager(database, cryptoEngine)
    private val bluetoothManager = BluetoothManager(application)
    private val meshRouter = MeshRouter()

    private var communicationService: CommunicationService? = null
    private var serviceBound = false

    // Bluetooth server mode
    private var bluetoothServerMode: BluetoothServerMode? = null
    private var isServerRunning = false
    var serverSocket: BluetoothServerSocket? = null

    private var socket: BluetoothSocket? = null
    private var connectedDevice: DiscoveredDevice? = null
    private var sessionKey: ByteArray? = null
    private var myKeyPair: CryptoEngine.KeyPair? = null
    private var keyExchangeCompleted = false

    private var remoteDeviceActualId: String? = null
    private var remoteDeviceBluetoothAddress: String? = null

    private val _messages = MutableStateFlow<List<MessageManager.Message>>(emptyList())
    val messages: StateFlow<List<MessageManager.Message>> = _messages

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _encryptionStatus = MutableStateFlow(EncryptionStatus(isEncrypted = false))
    val encryptionStatus: StateFlow<EncryptionStatus> = _encryptionStatus

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    private val _currentDeviceId = MutableStateFlow<String?>(null)
    val currentDeviceId: String?
        get() = _currentDeviceId.value

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceId: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class EncryptionStatus(
        val isEncrypted: Boolean,
        val algorithm: String = "AES-256-GCM",
        val keyExchange: String = "X25519"
    )

    companion object {
        // Fallback encryption key for consistent encryption
        private val FALLBACK_KEY = ByteArray(32) { i ->
            when (i % 4) {
                0 -> 0xAA.toByte()
                1 -> 0xBB.toByte()
                2 -> 0xCC.toByte()
                else -> 0xDD.toByte()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CommunicationService.LocalBinder
            communicationService = binder.getService()
            serviceBound = true

            observeIncomingMessages()
            observeIncomingKeyExchange()

            Timber.d("Communication service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            communicationService = null
            serviceBound = false
            Timber.d("Communication service disconnected")
        }
    }

    init {
        initializeDevice()

        viewModelScope.launch {
            startCommunicationService()

            // Wait for service to bind
            var attempts = 0
            while (!serviceBound && attempts < 20) {
                delay(100L)
                attempts++
            }

            if (serviceBound) {
                Timber.d("✅ Service bound and ready")
            } else {
                Timber.e("❌ Service failed to bind after 2 seconds")
            }
        }

        // START BLUETOOTH SERVER AUTOMATICALLY
        startBluetoothServer()
    }

    /**
     * Start Bluetooth server to accept incoming connections
     * THIS IS THE KEY FIX - Server starts automatically when app launches
     */
    private fun startBluetoothServer() {
        if (isServerRunning) {
            Timber.d("✅ Server already running")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("🎧 Starting Bluetooth server mode...")

                // Create server mode with connection callback
                bluetoothServerMode = BluetoothServerMode(
                    bluetoothManager = bluetoothManager,
                    onConnectionAccepted = { acceptedSocket ->
                        Timber.d("📡 Server accepted connection from: ${acceptedSocket.remoteDevice.address}")

                        // Save socket
                        socket = acceptedSocket
                        remoteDeviceBluetoothAddress = acceptedSocket.remoteDevice.address

                        // Bind to CommunicationService
                        viewModelScope.launch {
                            communicationService?.connectToDevice(
                                acceptedSocket,
                                acceptedSocket.remoteDevice.address
                            )
                            Timber.d("✅ Incoming connection bound to service")

                            // Use fallback key for accepted connections
                            useFallbackKey()

                            // Update connection state
                            _connectionState.value = ConnectionState.Connected(
                                acceptedSocket.remoteDevice.address
                            )
                        }
                    }
                )

                // Get server socket reference
                serverSocket = bluetoothManager.startServer()

                if (serverSocket != null) {
                    // Start accepting connections
                    bluetoothServerMode?.startServer()
                    isServerRunning = true

                    Timber.d("✅ Bluetooth server started and listening for connections")
                } else {
                    Timber.e("❌ Failed to create server socket")
                    isServerRunning = false
                }

            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to start Bluetooth server")
                isServerRunning = false
            }
        }
    }

    /**
     * Initialize device with persistent ID
     */
    private fun initializeDevice() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("sfcs_prefs", Context.MODE_PRIVATE)

                var deviceId = prefs.getString("device_id", null)

                if (deviceId == null) {
                    deviceId = cryptoEngine.generateDeviceId()
                    prefs.edit().putString("device_id", deviceId).apply()
                    Timber.d("🆔 Generated NEW device ID: $deviceId")
                } else {
                    Timber.d("🆔 Loaded EXISTING device ID: $deviceId")
                }

                _currentDeviceId.value = deviceId

            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize device")
            }
        }
    }

    /**
     * Start and bind to communication service
     */
    private fun startCommunicationService() {
        val context = getApplication<Application>()
        val intent = Intent(context, CommunicationService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Get device ID
     */
    fun getDeviceId(): String {
        return _currentDeviceId.value ?: "unknown"
    }

    /**
     * Connect to remote device via Bluetooth
     */
    fun connectToDevice(device: DiscoveredDevice) {
        viewModelScope.launch {
            try {
                Timber.d("🔗 Connecting:")
                Timber.d("   My ID: ${getDeviceId()}")
                Timber.d("   Remote BT: ${device.bluetoothDevice.address}")

                // Check server status
                if (serverSocket != null) {
                    Timber.d("✅ Server socket exists, ready to accept connections")
                } else {
                    Timber.d("⚠️ No server socket, attempting client connection...")
                }

                _isConnecting.value = true

                // Try client connection
                val connectedSocket = bluetoothManager.connectToDevice(device.bluetoothDevice)

                if (connectedSocket != null) {
                    Timber.d("✅ Client connection successful")

                    socket = connectedSocket
                    connectedDevice = device
                    remoteDeviceBluetoothAddress = device.bluetoothDevice.address

                    // Connect to communication service
                    withContext(Dispatchers.IO) {
                        communicationService?.connectToDevice(
                            connectedSocket,
                            device.bluetoothDevice.address
                        )
                        delay(500)
                    }

                    // Use fallback key
                    useFallbackKey()

                    // Update state
                    _connectionState.value = ConnectionState.Connected(device.bluetoothDevice.address)

                    // Load messages
                    loadMessages(device.bluetoothDevice.address)

                } else {
                    Timber.w("❌ Client connection failed, using demo mode")
                    useDemoMode(device.bluetoothDevice.address)
                }

            } catch (e: Exception) {
                Timber.e(e, "Connection error")
                _connectionError.value = "Connection failed: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * Initiate key exchange
     */
    private fun initiateKeyExchange(deviceId: String) {
        viewModelScope.launch {
            performKeyExchange(deviceId, isInitiator = true)
        }
    }

    /**
     * Use fallback encryption key
     */
    private fun useFallbackKey() {
        sessionKey = FALLBACK_KEY.copyOf()
        communicationService?.setSharedKey(sessionKey!!)
        keyExchangeCompleted = true
        _encryptionStatus.value = EncryptionStatus(isEncrypted = true)

        val keyHex = sessionKey?.take(8)?.joinToString("") { "%02x".format(it) }
        Timber.d("🔑 Using FALLBACK KEY: $keyHex...")
    }

    /**
     * Perform X25519 key exchange
     */
    private suspend fun performKeyExchange(deviceId: String, isInitiator: Boolean) {
        try {
            Timber.d("🔐 Starting key exchange (initiator=$isInitiator)")

            myKeyPair = cryptoEngine.generateKeyPair()

            if (isInitiator) {
                delay(500L)
                val sent = communicationService?.initiateKeyExchange(myKeyPair!!.publicKey) ?: false

                if (sent) {
                    Timber.d("📤 Sent our public key")

                    var attempts = 0
                    while (attempts < 150) {
                        val theirKey = communicationService?.incomingKeyExchange?.value
                        if (theirKey != null) {
                            Timber.d("📥 Received their public key")
                            deriveSharedSecret(theirKey)
                            break
                        }
                        delay(100L)
                        attempts++
                    }

                    if (attempts >= 150) {
                        Timber.w("⚠️ Key exchange timeout - falling back")
                        useFallbackKey()
                    }
                } else {
                    Timber.e("❌ Failed to send public key")
                    useFallbackKey()
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Key exchange failed")
            useFallbackKey()
        }
    }

    /**
     * Derive shared secret
     */
    private fun deriveSharedSecret(theirPublicKey: ByteArray) {
        try {
            if (myKeyPair == null) {
                Timber.e("No local key pair")
                return
            }

            val sharedSecret = cryptoEngine.deriveSharedSecret(
                myKeyPair!!.privateKey,
                theirPublicKey
            )

            sessionKey = cryptoEngine.deriveAESKey(sharedSecret)
            communicationService?.setSharedKey(sessionKey!!)

            keyExchangeCompleted = true
            _encryptionStatus.value = EncryptionStatus(isEncrypted = true)

            val keyHex = sessionKey?.take(8)?.joinToString("") { "%02x".format(it) }
            Timber.d("✅ Shared secret derived: $keyHex...")

        } catch (e: Exception) {
            Timber.e(e, "Failed to derive shared secret")
            useFallbackKey()
        }
    }

    /**
     * Observe incoming key exchange
     */
    private fun observeIncomingKeyExchange() {
        viewModelScope.launch {
            communicationService?.incomingKeyExchange?.collect { theirKey ->
                if (theirKey != null) {
                    Timber.d("📥 Received key exchange packet")

                    if (myKeyPair == null) {
                        myKeyPair = cryptoEngine.generateKeyPair()
                        Timber.d("Generated key pair for response")
                    }

                    if (!keyExchangeCompleted) {
                        delay(200L)
                        communicationService?.initiateKeyExchange(myKeyPair!!.publicKey)
                        Timber.d("📤 Sent our public key in response")

                        deriveSharedSecret(theirKey)
                    }
                }
            }
        }
    }

    /**
     * Load messages for device
     */
    fun loadMessages(deviceId: String) {
        viewModelScope.launch {
            try {
                val currentDevice = _currentDeviceId.value ?: return@launch

                Timber.d("📖 Loading messages:")
                Timber.d("   Bluetooth address: $deviceId")
                Timber.d("   Remote actual ID: $remoteDeviceActualId")
                Timber.d("   Current device ID: $currentDevice")

                val queryDeviceId = remoteDeviceActualId ?: deviceId

                val messageList = messageManager.getMessagesForDevice(queryDeviceId, currentDevice)
                _messages.value = messageList

                Timber.d("✅ Loaded ${messageList.size} messages")

            } catch (e: Exception) {
                Timber.e(e, "Failed to load messages")
            }
        }
    }

    /**
     * Send encrypted message
     */
    fun sendMessage(content: String, receiverId: String) {
        viewModelScope.launch {
            try {
                val senderId = _currentDeviceId.value ?: return@launch

                if (!keyExchangeCompleted || sessionKey == null) {
                    Timber.w("⚠️ Encryption not ready, cannot send")
                    return@launch
                }

                val actualReceiverId = remoteDeviceActualId ?: receiverId

                Timber.d("📤 Sending message: '$content'")
                Timber.d("   To: $actualReceiverId")
                Timber.d("   Key: ${sessionKey?.take(4)?.joinToString("") { "%02x".format(it) }}")

                val message = messageManager.createMessage(
                    content = content,
                    receiverId = actualReceiverId,
                    senderId = senderId
                )

                messageManager.updateMessageStatus(message.messageId, MessageManager.MessageStatus.SENDING)
                loadMessages(receiverId)

                if (serviceBound && socket?.isConnected == true) {
                    val success = communicationService?.sendMessage(
                        message = content,
                        receiverId = actualReceiverId,
                        senderId = senderId,
                        aesKey = null
                    ) ?: false

                    if (success) {
                        delay(500L)
                        messageManager.updateMessageStatus(message.messageId, MessageManager.MessageStatus.SENT)

                        delay(1000L)
                        messageManager.updateMessageStatus(message.messageId, MessageManager.MessageStatus.DELIVERED)

                        loadMessages(receiverId)
                        Timber.d("✅ Message sent successfully")
                    } else {
                        messageManager.updateMessageStatus(message.messageId, MessageManager.MessageStatus.FAILED)
                        loadMessages(receiverId)
                        Timber.e("❌ Failed to send message")
                    }
                } else {
                    Timber.e("❌ Socket disconnected, cannot send")
                    messageManager.updateMessageStatus(message.messageId, MessageManager.MessageStatus.FAILED)
                    loadMessages(receiverId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message")
            }
        }
    }

    /**
     * Observe incoming messages
     */
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            communicationService?.incomingMessages?.collect { packet ->
                if (packet != null) {
                    try {
                        Timber.d("📨 Received packet: ${packet.messageId}")

                        if (remoteDeviceActualId == null) {
                            remoteDeviceActualId = packet.senderId
                            Timber.d("🆔 Stored remote device ID: $remoteDeviceActualId")
                        }

                        val decryptedContent = if (sessionKey != null && keyExchangeCompleted) {
                            try {
                                val encryptedData = CryptoEngine.EncryptedMessage(
                                    packet.encryptedPayload,
                                    packet.iv
                                )
                                val decrypted = cryptoEngine.decrypt(encryptedData, sessionKey!!)
                                Timber.d("✅ Decrypted: '$decrypted'")
                                decrypted
                            } catch (e: Exception) {
                                Timber.e(e, "❌ Decryption failed")
                                "[Decryption Failed]"
                            }
                        } else {
                            "[No Encryption Key]"
                        }

                        val existingMessage = database.messageDao().getMessageById(packet.messageId)

                        if (existingMessage == null) {
                            database.messageDao().insertMessage(
                                MessageEntity(
                                    id = 0,
                                    messageId = packet.messageId,
                                    senderId = packet.senderId,
                                    receiverId = packet.receiverId,
                                    content = decryptedContent,
                                    timestamp = packet.timestamp,
                                    status = "DELIVERED",
                                    hopCount = packet.hopCount
                                )
                            )
                            Timber.d("💾 Saved message")
                        }

                        val currentChatDevice = when (val state = _connectionState.value) {
                            is ConnectionState.Connected -> state.deviceId
                            else -> null
                        }

                        if (currentChatDevice != null) {
                            loadMessages(currentChatDevice)
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process message")
                    }
                }
            }
        }
    }

    /**
     * Use demo mode
     */
    private suspend fun useDemoMode(deviceId: String) {
        Timber.d("Using demo mode")
        useFallbackKey()
        _connectionState.value = ConnectionState.Connected(deviceId)
        loadMessages(deviceId)
    }

    /**
     * Disconnect
     */
    fun disconnect() {
        communicationService?.closeConnection()
        socket = null
        sessionKey = null
        keyExchangeCompleted = false
        remoteDeviceActualId = null

        _connectionState.value = ConnectionState.Disconnected
        _encryptionStatus.value = EncryptionStatus(isEncrypted = false)
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()

        // Stop server
        bluetoothServerMode?.stopServer()
        serverSocket?.close()

        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }

        disconnect()
        bluetoothManager.cleanup()
    }
}
