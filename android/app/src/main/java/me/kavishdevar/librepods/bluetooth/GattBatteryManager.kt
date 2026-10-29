/*
    PodMate - a LibrePods fork
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package me.kavishdevar.librepods.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * Reads the standard GATT Battery Service (0x180F / 0x2A19) from the earbuds.
 *
 * Many clone/"fake" AirPods (and some genuine devices) expose battery here —
 * it's the mechanism Android's quick settings uses too. This works on any
 * phone with the Nearby-connect permission only, no root required — it's the
 * last-resort battery source when neither Apple's BLE status advertisements
 * nor the L2CAP control channel are usable.
 */
class GattBatteryManager(
    private val context: Context,
    private val onBatteryLevel: (level: Int) -> Unit
) {
    private companion object {
        const val TAG = "GattBattery"
        val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val MAX_RETRIES = 2
    }

    private var gatt: BluetoothGatt? = null
    private var retries = 0
    private var lastReported = -1
    private var servicePresent = true

    /**
     * Connects and subscribes. [onBatteryLevel] fires on every reported level
     * (initial read + live updates).
     */
    @SuppressLint("MissingPermission") // caller checks BLUETOOTH_CONNECT
    fun connect(device: BluetoothDevice) {
        stop()
        try {
            Log.d(TAG, "Connecting to GATT battery service of ${device.address}")
            gatt = device.connectGatt(context, false, callback)
        } catch (e: Exception) {
            Log.w(TAG, "connectGatt failed: $e")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private fun report(level: Int) {
        if (level == lastReported) return
        lastReported = level
        onBatteryLevel(level)
    }

    @SuppressLint("MissingPermission")
    private fun reconnect() {
        val device = gatt?.device
        gatt?.close()
        gatt = null
        if (device != null && retries < MAX_RETRIES) {
            retries++
            Log.d(TAG, "GATT retry #$retries")
            connect(device)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, status: Int, newState: Int
        ) {
            @Suppress("DEPRECATION")
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                try {
                    gatt.discoverServices()
                } catch (e: Exception) {
                    Log.w(TAG, "discoverServices failed: $e")
                    reconnect()
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                reconnect()
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Services discovery failed: $status")
                reconnect()
                return
            }
            val characteristic = try {
                gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
            } catch (e: Exception) {
                null
            }
            if (characteristic == null) {
                // No battery service — nothing we can do via GATT on this device.
                Log.d(TAG, "No GATT battery service on this device")
                servicePresent = false
                gatt.disconnect()
                return
            }
            try {
                @Suppress("DEPRECATION")
                gatt.readCharacteristic(characteristic)
                if (gatt.setCharacteristicNotification(characteristic, true)) {
                    characteristic.getDescriptor(CCC_DESCRIPTOR)?.let { descriptor ->
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                }
                Log.d(TAG, "Subscribed to GATT battery level")
            } catch (e: Exception) {
                Log.w(TAG, "GATT subscribe failed: $e")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            @Suppress("DEPRECATION")
            val value = try {
                characteristic.value
            } catch (_: Exception) {
                null
            }
            if (status == BluetoothGatt.GATT_SUCCESS && !value.isNullOrEmpty()) {
                report(value[0].toInt() and 0xFF)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = try {
                characteristic.value
            } catch (_: Exception) {
                null
            }
            if (!value.isNullOrEmpty()) {
                report(value[0].toInt() and 0xFF)
            }
        }
    }
}
