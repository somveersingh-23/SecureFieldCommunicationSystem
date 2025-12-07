package com.kaidwal.securefieldcommunicationsystem

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * WiFi Direct Manager - FULLY FUNCTIONAL
 * Handles real P2P WiFi connections
 */
@SuppressLint("MissingPermission")
class WiFiDirectManager(private val context: Context) {

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null
    private var broadcastReceiver: WiFiDirectBroadcastReceiver? = null

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    companion object {
        private const val SERVER_PORT = 8888
    }

    init {
        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
        Timber.d("WiFi Direct Manager initialized")
    }

    /**
     * Start peer discovery - REAL
     */
    fun discoverPeers() {
        registerWiFiDirectReceiver()

        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("WiFi Direct peer discovery started")
            }

            override fun onFailure(reason: Int) {
                Timber.w("WiFi Direct peer discovery failed: $reason")
            }
        })
    }

    /**
     * Register WiFi Direct broadcast receiver
     */
    private fun registerWiFiDirectReceiver() {
        if (broadcastReceiver != null) return

        broadcastReceiver = WiFiDirectBroadcastReceiver(
            manager = wifiP2pManager,
            channel = channel,
            onPeersChanged = {
                requestPeers()
            },
            onConnectionChanged = {
                requestConnectionInfo()
            },
            onStateChanged = { isEnabled ->
                Timber.d("WiFi P2P state: $isEnabled")
            }
        )

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        context.registerReceiver(broadcastReceiver, filter)
    }

    /**
     * Request peers list
     */
    private fun requestPeers() {
        wifiP2pManager?.requestPeers(channel) { peerList ->
            _peers.value = peerList.deviceList.toList()
            Timber.d("Found ${peerList.deviceList.size} WiFi Direct peers")
        }
    }

    /**
     * Stop peer discovery
     */
    fun stopPeerDiscovery() {
        wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("WiFi Direct peer discovery stopped")
            }

            override fun onFailure(reason: Int) {
                Timber.w("Failed to stop peer discovery: $reason")
            }
        })

        unregisterWiFiDirectReceiver()
    }

    /**
     * Unregister receiver
     */
    private fun unregisterWiFiDirectReceiver() {
        try {
            if (broadcastReceiver != null) {
                context.unregisterReceiver(broadcastReceiver)
                broadcastReceiver = null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering receiver")
        }
    }

    /**
     * Connect to peer - REAL
     */
    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0
        }

        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("WiFi Direct connection initiated to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                Timber.w("WiFi Direct connection failed: $reason")
            }
        })
    }

    /**
     * Request connection info - REAL
     */
    fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(channel) { info ->
            _connectionInfo.value = info
            Timber.d("WiFi Direct connection info: isGroupOwner=${info.groupOwnerAddress}")
        }
    }

    /**
     * Start server (Group Owner) - REAL
     */
    suspend fun startServer(): ServerSocket? = withContext(Dispatchers.IO) {
        return@withContext try {
            serverSocket = ServerSocket(SERVER_PORT)
            Timber.d("WiFi Direct server started on port $SERVER_PORT")
            serverSocket
        } catch (e: IOException) {
            Timber.e(e, "Failed to start WiFi Direct server")
            null
        }
    }

    /**
     * Accept incoming connection - REAL
     */
    suspend fun acceptConnection(serverSocket: ServerSocket): Socket? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                Timber.d("Waiting for WiFi Direct connection...")
                val socket = serverSocket.accept()
                Timber.d("WiFi Direct connection accepted from: ${socket.inetAddress}")
                socket
            } catch (e: IOException) {
                Timber.e(e, "Failed to accept WiFi Direct connection")
                null
            }
        }

    /**
     * Connect to group owner - REAL
     */
    suspend fun connectToGroupOwner(groupOwnerAddress: String): Socket? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                Timber.d("Connecting to group owner: $groupOwnerAddress")
                val socket = Socket()
                socket.connect(InetSocketAddress(groupOwnerAddress, SERVER_PORT), 5000)
                clientSocket = socket
                Timber.d("Connected to group owner")
                socket
            } catch (e: IOException) {
                Timber.e(e, "Failed to connect to group owner")
                null
            }
        }

    /**
     * Send data via WiFi Direct - REAL
     */
    suspend fun sendData(socket: Socket, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                socket.getOutputStream().write(data)
                socket.getOutputStream().flush()
                Timber.d("Sent ${data.size} bytes via WiFi Direct")
                true
            } catch (e: IOException) {
                Timber.e(e, "Failed to send data via WiFi Direct")
                false
            }
        }

    /**
     * Receive data via WiFi Direct - REAL
     */
    suspend fun receiveData(socket: Socket): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            val buffer = ByteArray(4096)
            val bytes = socket.getInputStream().read(buffer)
            if (bytes > 0) {
                buffer.copyOf(bytes)
            } else {
                null
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to receive data via WiFi Direct")
            null
        }
    }

    /**
     * Remove group (disconnect) - REAL
     */
    fun removeGroup() {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.d("WiFi Direct group removed")
            }

            override fun onFailure(reason: Int) {
                Timber.w("Failed to remove group: $reason")
            }
        })
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        stopPeerDiscovery()
        try {
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            Timber.e(e, "Error closing WiFi Direct sockets")
        }
        removeGroup()
    }
}
