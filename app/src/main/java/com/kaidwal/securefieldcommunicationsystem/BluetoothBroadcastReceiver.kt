package com.kaidwal.securefieldcommunicationsystem

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Bluetooth Broadcast Receiver
 * Listens for Bluetooth events
 */
class BluetoothBroadcastReceiver : BroadcastReceiver() {

    var onDeviceDiscovered: ((BluetoothDevice) -> Unit)? = null
    var onDiscoveryFinished: (() -> Unit)? = null
    var onBluetoothStateChanged: ((Int) -> Unit)? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothDevice.ACTION_FOUND -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    Timber.d("Device found: ${it.name ?: "Unknown"} - ${it.address}")
                    onDeviceDiscovered?.invoke(it)
                }
            }

            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                Timber.d("Discovery started")
            }

            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                Timber.d("Discovery finished")
                onDiscoveryFinished?.invoke()
            }

            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        Timber.d("Device bonded: ${device?.name}")
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        Timber.d("Device bonding: ${device?.name}")
                    }
                    BluetoothDevice.BOND_NONE -> {
                        Timber.d("Device bond removed: ${device?.name}")
                    }
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                onBluetoothStateChanged?.invoke(state)

                when (state) {
                    BluetoothAdapter.STATE_OFF -> Timber.d("Bluetooth turned OFF")
                    BluetoothAdapter.STATE_ON -> Timber.d("Bluetooth turned ON")
                    BluetoothAdapter.STATE_TURNING_OFF -> Timber.d("Bluetooth turning OFF")
                    BluetoothAdapter.STATE_TURNING_ON -> Timber.d("Bluetooth turning ON")
                }
            }
        }
    }
}
