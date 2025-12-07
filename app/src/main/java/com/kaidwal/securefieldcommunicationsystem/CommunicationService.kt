package com.kaidwal.securefieldcommunicationsystem

import android.app.*
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Communication Service - PRODUCTION VERSION WITH VOICE STREAMING
 * Handles background Bluetooth/WiFi communication with end-to-end encryption
 */
class CommunicationService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cryptoEngine = CryptoEngine()
    private lateinit var database: DeviceDatabase

    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isListening = false
    private var sharedAESKey: ByteArray? = null

    // Voice packet callback
    private var voicePacketListener: ((String, ByteArray) -> Unit)? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _incomingMessages = MutableStateFlow<MessagePacket?>(null)
    val incomingMessages: StateFlow<MessagePacket?> = _incomingMessages

    private val _incomingKeyExchange = MutableStateFlow<ByteArray?>(null)
    val incomingKeyExchange: StateFlow<ByteArray?> = _incomingKeyExchange

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceId: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class MessagePacket(
        val messageId: String,
        val senderId: String,
        val receiverId: String,
        val encryptedPayload: ByteArray,
        val iv: ByteArray,
        val timestamp: Long,
        val hopCount: Int = 0
    ) {
        fun toBytes(): ByteArray {
            val msgIdBytes = messageId.padEnd(32).take(32).toByteArray()
            val senderBytes = senderId.padEnd(32).take(32).toByteArray()
            val receiverBytes = receiverId.padEnd(32).take(32).toByteArray()
            val timestampBytes = timestamp.toByteArray()
            val hopCountBytes = hopCount.toByteArray()
            val ivLenBytes = iv.size.toByteArray()
            val payloadLenBytes = encryptedPayload.size.toByteArray()

            return msgIdBytes + senderBytes + receiverBytes + timestampBytes +
                    hopCountBytes + ivLenBytes + iv + payloadLenBytes + encryptedPayload
        }

        companion object {
            fun fromBytes(data: ByteArray): MessagePacket? {
                return try {
                    var offset = 0

                    val messageId = String(data.copyOfRange(offset, offset + 32)).trim()
                    offset += 32

                    val senderId = String(data.copyOfRange(offset, offset + 32)).trim()
                    offset += 32

                    val receiverId = String(data.copyOfRange(offset, offset + 32)).trim()
                    offset += 32

                    val timestamp = data.copyOfRange(offset, offset + 8).toLong()
                    offset += 8

                    val hopCount = data.copyOfRange(offset, offset + 4).toInt()
                    offset += 4

                    val ivLen = data.copyOfRange(offset, offset + 4).toInt()
                    offset += 4

                    val iv = data.copyOfRange(offset, offset + ivLen)
                    offset += ivLen

                    val payloadLen = data.copyOfRange(offset, offset + 4).toInt()
                    offset += 4

                    val payload = data.copyOfRange(offset, offset + payloadLen)

                    MessagePacket(messageId, senderId, receiverId, payload, iv, timestamp, hopCount)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse message packet")
                    null
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): CommunicationService = this@CommunicationService
    }

    override fun onCreate() {
        super.onCreate()
        database = DeviceDatabase.getInstance(applicationContext)
        Timber.d("CommunicationService created")
        startForeground()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("CommunicationService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        serviceScope.cancel()
        closeConnection()
        Timber.d("CommunicationService destroyed")
    }

    private fun startForeground() {
        val channelId = createNotificationChannel()

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SFCS Active")
            .setContentText("Secure communication running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel(): String {
        val channelId = "sfcs_communication"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SFCS Communication",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Secure Field Communication Service"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return channelId
    }

    fun connectToDevice(socket: BluetoothSocket, deviceId: String) {
        serviceScope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting

                activeSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                isListening = true

                _connectionState.value = ConnectionState.Connected(deviceId)

                Timber.d("Connected to device: $deviceId, starting listener...")
                startListening()
                Timber.d("Listener started for device: $deviceId")
            } catch (e: Exception) {
                Timber.e(e, "Connection failed")
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun initiateKeyExchange(publicKey: ByteArray): Boolean {
        return try {
            if (activeSocket?.isConnected != true) {
                Timber.e("Socket not connected")
                return false
            }

            val packet = ByteArray(1 + 4 + publicKey.size)
            packet[0] = 0x01.toByte()

            packet[1] = ((publicKey.size shr 24) and 0xFF).toByte()
            packet[2] = ((publicKey.size shr 16) and 0xFF).toByte()
            packet[3] = ((publicKey.size shr 8) and 0xFF).toByte()
            packet[4] = (publicKey.size and 0xFF).toByte()

            System.arraycopy(publicKey, 0, packet, 5, publicKey.size)

            synchronized(this) {
                outputStream?.write(packet)
                outputStream?.flush()
                Timber.d("📤 Sent key exchange packet: ${publicKey.size} bytes")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to send key exchange")
            false
        }
    }

    fun setSharedKey(key: ByteArray) {
        sharedAESKey = key
        Timber.d("🔐 Shared AES key established: ${key.size} bytes")
    }

    fun sendMessage(
        message: String,
        receiverId: String,
        senderId: String,
        aesKey: ByteArray? = null
    ): Boolean {
        return try {
            if (activeSocket?.isConnected != true) {
                Timber.e("Socket not connected, cannot send message")
                return false
            }

            val keyToUse = aesKey ?: sharedAESKey

            if (keyToUse == null) {
                Timber.e("No encryption key available")
                return false
            }

            val encrypted = cryptoEngine.encrypt(message, keyToUse)

            val packet = MessagePacket(
                messageId = cryptoEngine.generateMessageId(),
                senderId = senderId,
                receiverId = receiverId,
                encryptedPayload = encrypted.ciphertext,
                iv = encrypted.iv,
                timestamp = System.currentTimeMillis(),
                hopCount = 0
            )

            val packetBytes = packet.toBytes()

            synchronized(this) {
                try {
                    val fullPacket = ByteArray(1 + packetBytes.size)
                    fullPacket[0] = 0x02.toByte()
                    System.arraycopy(packetBytes, 0, fullPacket, 1, packetBytes.size)

                    outputStream?.write(fullPacket)
                    outputStream?.flush()
                    Timber.d("📤 Sent message: ${packet.messageId}")
                    true
                } catch (e: IOException) {
                    Timber.e(e, "Failed to send message - socket broken")
                    _connectionState.value = ConnectionState.Disconnected
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to send message")
            false
        }
    }

    /**
     * Send raw data packet (for voice streaming)
     */
    fun sendRawData(data: ByteArray): Boolean {
        return try {
            if (activeSocket?.isConnected != true) {
                Timber.e("Socket not connected")
                return false
            }

            synchronized(this) {
                outputStream?.write(data)
                outputStream?.flush()
                Timber.v("Sent ${data.size} bytes raw data")
                true
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to send raw data")
            _connectionState.value = ConnectionState.Disconnected
            false
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error sending data")
            false
        }
    }

    /**
     * Set voice packet listener
     */
    fun setVoicePacketListener(listener: (String, ByteArray) -> Unit) {
        voicePacketListener = listener
        Timber.d("Voice packet listener registered")
    }

    private fun startListening() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                Timber.d("🎧 Starting message listener thread...")
                val buffer = ByteArray(8192)

                while (isListening && activeSocket?.isConnected == true) {
                    try {
                        val bytesRead = inputStream?.read(buffer) ?: -1

                        if (bytesRead > 0) {
                            Timber.d("📥 Read $bytesRead bytes from socket")

                            val packetType = buffer[0]

                            when (packetType.toInt()) {
                                0x01 -> {
                                    Timber.d("🔑 Received key exchange packet")
                                    handleKeyExchangePacket(buffer.copyOfRange(1, bytesRead))
                                }

                                0x02 -> {
                                    val packetData = buffer.copyOfRange(1, bytesRead)
                                    val packet = MessagePacket.fromBytes(packetData)

                                    if (packet != null) {
                                        Timber.d("📨 Received message packet: ${packet.messageId}")
                                        _incomingMessages.value = packet
                                        saveIncomingMessageToDatabase(packet)
                                    } else {
                                        Timber.w("Failed to parse message packet")
                                    }
                                }

                                0x03 -> {
                                    Timber.d("🎙️ Received voice packet")
                                    handleVoicePacket(buffer.copyOfRange(1, bytesRead))
                                }

                                else -> {
                                    Timber.w("⚠️ Unknown packet type: $packetType")
                                }
                            }

                        } else if (bytesRead == -1) {
                            Timber.w("Connection closed by remote device")
                            break
                        }
                    } catch (e: IOException) {
                        if (isListening) {
                            Timber.e(e, "Error reading message")
                        }
                        break
                    } catch (e: Exception) {
                        Timber.e(e, "Unexpected error in message reading")
                        break
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Listening stopped")
            } finally {
                _connectionState.value = ConnectionState.Disconnected
                isListening = false
                Timber.d("Stopped listening for messages")
            }
        }
    }

    private fun handleKeyExchangePacket(data: ByteArray) {
        try {
            val keyLength = ((data[0].toInt() and 0xFF) shl 24) or
                    ((data[1].toInt() and 0xFF) shl 16) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    (data[3].toInt() and 0xFF)

            val publicKey = data.copyOfRange(4, 4 + keyLength)

            Timber.d("📥 Received public key: ${publicKey.size} bytes")
            _incomingKeyExchange.value = publicKey

        } catch (e: Exception) {
            Timber.e(e, "Failed to handle key exchange")
        }
    }

    /**
     * Handle incoming voice packet
     */
    private fun handleVoicePacket(data: ByteArray) {
        try {
            // Parse: [TIMESTAMP:8bytes][DATA_LENGTH:4bytes][ENCRYPTED_AUDIO]
            var offset = 0

            val timestamp = data.copyOfRange(offset, offset + 8).toLong()
            offset += 8

            val dataLength = data.copyOfRange(offset, offset + 4).toInt()
            offset += 4

            val encryptedAudio = data.copyOfRange(offset, offset + dataLength)

            Timber.d("🎙️ Voice packet: ${encryptedAudio.size} bytes, timestamp: $timestamp")

            // Get sender device ID from connection state
            val deviceId = when (val state = _connectionState.value) {
                is ConnectionState.Connected -> state.deviceId
                else -> "unknown"
            }

            // Notify listener (WalkieTalkieViewModel)
            voicePacketListener?.invoke(deviceId, encryptedAudio)

        } catch (e: Exception) {
            Timber.e(e, "Failed to handle voice packet")
        }
    }

    private suspend fun saveIncomingMessageToDatabase(packet: MessagePacket) {
        withContext(Dispatchers.IO) {
            try {
                val currentDeviceId = getCurrentDeviceId()

                val messageEntity = MessageEntity(
                    id = 0,
                    messageId = packet.messageId,
                    senderId = packet.senderId,
                    receiverId = currentDeviceId,
                    content = String(packet.encryptedPayload),
                    timestamp = packet.timestamp,
                    status = "DELIVERED",
                    hopCount = packet.hopCount
                )

                val insertedId = database.messageDao().insertMessage(messageEntity)

                if (insertedId > 0) {
                    Timber.d("💾 Saved incoming message: ${packet.messageId} (DB ID: $insertedId)")
                } else {
                    Timber.w("Message already exists: ${packet.messageId}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save incoming message")
            }
        }
    }

    private fun getCurrentDeviceId(): String {
        val prefs = applicationContext.getSharedPreferences("sfcs_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)

        if (deviceId == null) {
            deviceId = CryptoEngine().generateDeviceId()
            prefs.edit().putString("device_id", deviceId).apply()
            Timber.d("Generated new device ID: $deviceId")
        }

        return deviceId
    }

    fun closeConnection() {
        isListening = false

        try {
            inputStream?.close()
            outputStream?.close()
            activeSocket?.close()

            activeSocket = null
            inputStream = null
            outputStream = null
            sharedAESKey = null

            _connectionState.value = ConnectionState.Disconnected

            Timber.d("Connection closed")
        } catch (e: IOException) {
            Timber.e(e, "Error closing connection")
        }
    }
}

// Extension functions
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

private fun ByteArray.toLong(): Long {
    return ((this[0].toLong() and 0xFF) shl 56) or
            ((this[1].toLong() and 0xFF) shl 48) or
            ((this[2].toLong() and 0xFF) shl 40) or
            ((this[3].toLong() and 0xFF) shl 32) or
            ((this[4].toLong() and 0xFF) shl 24) or
            ((this[5].toLong() and 0xFF) shl 16) or
            ((this[6].toLong() and 0xFF) shl 8) or
            (this[7].toLong() and 0xFF)
}

private fun ByteArray.toInt(): Int {
    return ((this[0].toInt() and 0xFF) shl 24) or
            ((this[1].toInt() and 0xFF) shl 16) or
            ((this[2].toInt() and 0xFF) shl 8) or
            (this[3].toInt() and 0xFF)
}
