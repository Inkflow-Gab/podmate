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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid
import android.util.Log
import java.io.IOException

/**
 * Opens Apple's Accessory Protocol control channel (classic L2CAP, PSM 0x1001)
 * **without root** — as far as the platform allows it.
 *
 * Android's own L2CAP client code is what makes this hard: on most devices the
 * stack negotiates an ERTM/flow-control mode that AirPods refuse, so the channel
 * is closed right after `connect()`. Fixing that inside the stack needs the
 * LSPosed hook (root). What we *can* do rootlessly, and what this class does:
 *
 *  - create the socket through the hidden `BluetoothSocket` constructors, which
 *    only needs the hidden-API exemption PodMate already applies from JNI,
 *  - retry a few times (the first attempt right after the ACL link comes up is
 *    often too early and fails with EBUSY/"socket closed"),
 *  - enforce a hard timeout so a hanging `connect()` can never freeze the app,
 *  - keep a human-readable diagnosis so Settings → Troubleshooting can tell the
 *    user *why* the control channel is unavailable on their phone.
 */
object L2capConnector {
    private const val TAG = "L2capConnector"

    /** Apple Accessory Protocol over classic L2CAP. */
    const val DEFAULT_PSM = 4097

    private val APPLE_UUID = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

    @Volatile
    private var lastDiagnosis: String = "No control-channel attempt yet"

    fun diagnosis(): String = lastDiagnosis

    /**
     * Creates and connects a control-channel socket, retrying a few times.
     * Returns a **connected** socket; throws if every attempt failed.
     */
    @SuppressLint("MissingPermission")
    fun openControlChannel(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        psm: Int = DEFAULT_PSM,
        attempts: Int = 3,
        connectTimeoutMs: Long = 6000L
    ): BluetoothSocket {
        val notes = mutableListOf<String>()
        var lastError: Exception? = null

        for (attempt in 1..attempts) {
            var socket: BluetoothSocket? = null
            try {
                val newSocket = createBluetoothSocket(adapter, device, APPLE_UUID, psm)
                socket = newSocket
                connectWithTimeout(newSocket, connectTimeoutMs)
                lastDiagnosis =
                    "Control channel open on ${device.address} (PSM $psm, attempt $attempt)"
                Log.d(TAG, lastDiagnosis)
                return newSocket
            } catch (e: Exception) {
                lastError = e
                val message = (e.message ?: e.javaClass.simpleName).take(120)
                notes += "try $attempt: $message"
                Log.w(TAG, "Attempt $attempt for ${device.address} (PSM $psm) failed: $message")
                socket?.let { closeQuietly(it) }
                if (attempt < attempts) {
                    try {
                        Thread.sleep(700L)
                    } catch (_: InterruptedException) {
                        // keep going
                    }
                }
            }
        }

        lastDiagnosis = buildString {
            append("PSM $psm to ${device.address} failed — ")
            append(notes.joinToString("; "))
            append(
                ". Android's Bluetooth stack has to accept the L2CAP channel: that works " +
                    "on Android 17/some OEM builds, needs the LSPosed hook elsewhere. " +
                    "No root gives battery + popups + media controls instead."
            )
        }
        Log.w(TAG, lastDiagnosis)
        throw lastError ?: IOException("Could not open the AirPods control channel")
    }

    /**
     * `BluetoothSocket.connect()` blocks with no timeout. Run it on a worker
     * thread and abort by closing the socket if it takes too long.
     */
    private fun connectWithTimeout(socket: BluetoothSocket, timeoutMs: Long) {
        var failure: Exception? = null
        val worker = Thread {
            try {
                socket.connect()
            } catch (e: Exception) {
                failure = e
            }
        }
        worker.isDaemon = true
        worker.start()
        try {
            worker.join(timeoutMs)
        } catch (_: InterruptedException) {
        }
        if (worker.isAlive) {
            closeQuietly(socket)
            throw IOException("connect timed out after ${timeoutMs}ms")
        }
        failure?.let { throw it }
        if (!socket.isConnected) throw IOException("socket did not connect")
    }

    fun closeQuietly(socket: BluetoothSocket?) {
        if (socket == null) return
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
