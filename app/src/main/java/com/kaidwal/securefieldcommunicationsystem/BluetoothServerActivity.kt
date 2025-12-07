package com.kaidwal.securefieldcommunicationsystem

import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class BluetoothServerMode(
    private val bluetoothManager: BluetoothManager,
    private val onConnectionAccepted: (BluetoothSocket) -> Unit
) {

    private var serverSocket: BluetoothServerSocket? = null
    private var isListening = false

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (isListening) {
            Timber.d("✅ Server already listening")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                isListening = true

                // Create server socket
                serverSocket = bluetoothManager.startServer()

                if (serverSocket != null) {
                    Timber.d("🎧 Bluetooth server started, waiting for connections...")

                    // Accept incoming connections (LOOP - FIXED!)
                    while (isListening) {
                        try {
                            Timber.d("⏳ Waiting for incoming connection...")
                            val socket = bluetoothManager.acceptConnection(serverSocket!!)

                            if (socket != null) {
                                Timber.d("✅ Accepted connection from: ${socket.remoteDevice.address}")

                                // Notify callback
                                onConnectionAccepted(socket)

                                // IMPORTANT: Continue accepting more connections
                                Timber.d("📡 Ready for next connection...")
                            }
                        } catch (e: Exception) {
                            if (isListening) {
                                Timber.e(e, "Error accepting connection, will retry...")
                                // Don't break - keep trying
                            } else {
                                break
                            }
                        }
                    }
                } else {
                    Timber.e("❌ Failed to create server socket")
                    isListening = false
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Server error")
                isListening = false
            } finally {
                Timber.d("🛑 Server loop ended")
            }
        }
    }

    fun stopServer() {
        isListening = false
        try {
            serverSocket?.close()
            serverSocket = null
            Timber.d("🛑 Bluetooth server stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping server")
        }
    }
}
