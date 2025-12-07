package com.kaidwal.securefieldcommunicationsystem

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Manager - FULLY FUNCTIONAL WITH SERVER SUPPORT
 * Handles all Bluetooth operations including server mode
 */
class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothReceiver = BluetoothBroadcastReceiver()

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private val activeSockets = mutableListOf<BluetoothSocket>()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Scanning : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val device: BluetoothDevice) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    companion object {
        private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_NAME = "SFCSService"
    }

    init {
        registerBluetoothReceiver()
        bluetoothReceiver.onDeviceDiscovered = { device ->
            if (!_discoveredDevices.value.any { it.address == device.address }) {
                val currentList = _discoveredDevices.value.toMutableList()
                currentList.add(device)
                _discoveredDevices.value = currentList

                Timber.d("Discovered new device: ${device.name ?: "Unknown"} - ${device.address}")
            }
        }
    }

    fun hasPermissions(): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for getting paired devices")
            emptySet()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        return try {
            _connectionState.value = ConnectionState.Scanning
            _discoveredDevices.value = emptyList()

            val result = bluetoothAdapter?.startDiscovery() ?: false
            if (result) {
                Timber.d("Started Bluetooth discovery")
            } else {
                Timber.w("Failed to start Bluetooth discovery")
            }
            result
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for discovery")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            _connectionState.value = ConnectionState.Idle
            Timber.d("Stopped Bluetooth discovery")
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for stopping discovery")
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(): BluetoothServerSocket? {
        return try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                SERVICE_NAME,
                SERVICE_UUID
            )
            Timber.d("Bluetooth server started")
            serverSocket
        } catch (e: IOException) {
            Timber.e(e, "Failed to start Bluetooth server")
            null
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for server")
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun acceptConnection(serverSocket: BluetoothServerSocket): BluetoothSocket? =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Waiting for incoming connection...")
                val socket = serverSocket.accept()

                if (socket != null) {
                    activeSockets.add(socket)
                    _connectionState.value = ConnectionState.Connected(socket.remoteDevice)
                    Timber.d("Incoming connection accepted from: ${socket.remoteDevice.address}")
                    socket
                } else {
                    Timber.w("Server socket returned null")
                    null
                }
            } catch (e: IOException) {
                Timber.e(e, "Error accepting connection")
                null
            }
        }

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: BluetoothDevice): BluetoothSocket? =
        withContext(Dispatchers.IO) {
            val existingSocket = activeSockets.find {
                it.remoteDevice.address == device.address && it.isConnected
            }

            if (existingSocket != null) {
                Timber.d("Reusing existing connection to: ${device.address}")
                return@withContext existingSocket
            }

            if (clientSocket?.isConnected == true) {
                try {
                    clientSocket?.close()
                    Timber.d("Closed old client socket")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to close old socket")
                }
            }

            var socket: BluetoothSocket? = null
            var attempts = 0
            val maxAttempts = 3

            while (socket == null && attempts < maxAttempts) {
                attempts++

                try {
                    _connectionState.value = ConnectionState.Connecting

                    Timber.d("Connection attempt $attempts/$maxAttempts to ${device.address}")

                    stopDiscovery()

                    socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)

                    try {
                        socket.connect()

                        if (socket.isConnected) {
                            clientSocket = socket
                            activeSockets.add(socket)

                            _connectionState.value = ConnectionState.Connected(device)
                            Timber.d("Successfully connected to: ${device.address}")
                            return@withContext socket
                        }
                    } catch (e: IOException) {
                        Timber.w(e, "Connection attempt $attempts failed")
                        socket?.close()
                        socket = null

                        if (attempts < maxAttempts) {
                            delay(1000)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in connection attempt $attempts")
                    socket?.close()
                    socket = null

                    if (attempts < maxAttempts) {
                        delay(1000)
                    }
                }
            }

            if (socket == null) {
                _connectionState.value = ConnectionState.Error("Connection failed after $maxAttempts attempts")
                Timber.e("Failed to connect to ${device.address} after $maxAttempts attempts")
            }

            return@withContext socket
        }

    @SuppressLint("MissingPermission")
    suspend fun sendData(socket: BluetoothSocket, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                socket.outputStream.write(data)
                socket.outputStream.flush()
                Timber.d("Sent ${data.size} bytes")
                true
            } catch (e: IOException) {
                Timber.e(e, "Failed to send data")
                false
            }
        }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        Timber.d("Bluetooth receiver registered")
    }

    fun cleanup() {
        try {
            stopDiscovery()

            activeSockets.forEach { socket ->
                try {
                    socket.close()
                } catch (e: IOException) {
                    Timber.w(e, "Failed to close socket")
                }
            }
            activeSockets.clear()

            serverSocket?.close()
            serverSocket = null

            clientSocket?.close()
            clientSocket = null

            context.unregisterReceiver(bluetoothReceiver)
            Timber.d("Bluetooth manager cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }
}
