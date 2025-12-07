package com.kaidwal.securefieldcommunicationsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import timber.log.Timber

/**
 * WiFi Direct Broadcast Receiver
 * Handles WiFi P2P events
 */
class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?,
    private val onPeersChanged: () -> Unit,
    private val onConnectionChanged: () -> Unit,
    private val onStateChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // WiFi P2P state changed
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED

                Timber.d("WiFi P2P state: ${if (isEnabled) "ENABLED" else "DISABLED"}")
                onStateChanged(isEnabled)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // Peer list changed
                Timber.d("WiFi P2P peers changed")
                manager?.requestPeers(channel) { peers ->
                    Timber.d("Found ${peers.deviceList.size} WiFi Direct peers")
                }
                onPeersChanged()
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // Connection state changed
                Timber.d("WiFi P2P connection changed")
                onConnectionChanged()
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // This device's details changed
                Timber.d("This device changed")
            }
        }
    }
}
