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
package me.kavishdevar.librepods.utils

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Light-weight Shizuku integration.
 *
 * Talks to the Shizuku server over its stable AIDL transaction codes
 * (see the IShizukuService / IRemoteProcess AIDLs in the Shizuku repo)
 * without needing a separate UserService, so we only need the tiny
 * `dev.rikka.shizuku:api` artifact.
 *
 * Used for small privileged helpers that work **without root**:
 * - reading the local Bluetooth MAC (`settings get secure bluetooth_address`)
 *   when the privileged LOCAL_MAC_ADDRESS permission is unavailable
 * - granting runtime permissions that the system dialogs misbehave with
 * - enabling screen overlays (used by the connection popup)
 */
object ShizukuState {
    private const val TAG = "ShizukuState"

    /** The Shizuku service binder interface name. */
    private const val SERVICE_DESCRIPTOR = "moe.shizuku.server.IShizukuService"
    private const val REMOTE_PROCESS_DESCRIPTOR = "moe.shizuku.server.IRemoteProcess"

    /**
     * The Shizuku server package: used to link users to it when it is missing.
     */
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_PLAY_PACKAGE = "moe.shizuku.manager"

    /** Transaction codes from IShizukuService.aidl (stable across versions). */
    private const val TXN_NEW_PROCESS = 7

    /** Transaction codes from IRemoteProcess.aidl (assigned by declaration order). */
    private const val TXN_PROC_GET_INPUT_STREAM = 2
    private const val TXN_PROC_WAIT_FOR = 4
    private const val TXN_PROC_DESTROY = 6

    /** Whether the Shizuku server is visible (Shizuku app running + bound). */
    @Volatile
    var isServerAvailable = false
        private set

    /** Whether PodMate has been granted access to the Shizuku server. */
    @Volatile
    var isGranted = false
        private set

    /** Registers the server-lifecycle listeners. Call once, e.g. from Application. */
    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky {
                try {
                    isServerAvailable = true
                    isGranted = Shizuku.checkSelfPermission() == 0
                    Log.d(TAG, "Shizuku server available (granted=$isGranted)")
                } catch (e: Exception) {
                    Log.w(TAG, "Shizuku checkSelfPermission failed: $e")
                }
            }
            Shizuku.addBinderDeadListener {
                isServerAvailable = false
                isGranted = false
            }
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                isGranted = grantResult == 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register Shizuku listeners: $e")
        }
    }

    /** Opens the Shizuku permission dialog (server must be available). */
    fun requestPermission() {
        try {
            if (isServerAvailable) {
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku.requestPermission failed: $e")
        }
    }

    class ShizukuUnavailableException(message: String) : Exception(message)

    data class ShellResult(val exitCode: Int, val stdout: String)

    /**
     * Runs a shell command inside the Shizuku server (uid 2000, "shell").
     * Returns null when Shizuku is unavailable/not granted or on errors.
     */
    fun runCommand(
        cmds: Array<String>,
        timeoutMs: Long = 4000
    ): ShellResult? {
        if (!isServerAvailable || !isGranted) return null
        return try {
            val processBinder = startRemoteProcess(cmds) ?: return null
            val result = readProcessOutput(processBinder, timeoutMs)
            destroyProcess(processBinder)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku command failed: ${e.message}")
            null
        }
    }

    private fun startRemoteProcess(cmds: Array<String>): IBinder? {
        val server = Shizuku.getBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val wrapper = ShizukuBinderWrapper(server)
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeStringArray(cmds)
            data.writeStringArray(null)
            data.writeString(null)
            if (!wrapper.transact(TXN_NEW_PROCESS, data, reply, 0)) return null
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun readProcessOutput(processBinder: IBinder, timeoutMs: Long): ShellResult? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR)
            val wrapper = ShizukuBinderWrapper(processBinder)
            if (!wrapper.transact(TXN_PROC_GET_INPUT_STREAM, data, reply, 0)) return null
            reply.readException()
            val stdoutFd = ParcelFileDescriptor.CREATOR.createFromParcel(reply)

            // A watchdog so a misbehaving command can never stall the caller.
            var watchdog: Thread? = null
            val processBinderRef = processBinder
            watchdog = Thread {
                try {
                    Thread.sleep(timeoutMs)
                    destroyProcess(processBinderRef)
                } catch (_: InterruptedException) {
                    // finished in time
                }
            }
            watchdog.isDaemon = true
            watchdog.start()

            val exitCode: Int
            var text: String
            try {
                ParcelFileDescriptor.AutoCloseInputStream(stdoutFd).use { stream ->
                    text = stream.bufferedReader().readText()
                }
                exitCode = try {
                    val data2 = Parcel.obtain()
                    val reply2 = Parcel.obtain()
                    try {
                        data2.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR)
                        if (wrapper.transact(TXN_PROC_WAIT_FOR, data2, reply2, 0)) {
                            reply2.readException()
                            reply2.readInt().also { if (it == 0) Log.d(TAG, "Shizuku command exited 0") }
                        } else -1
                    } finally {
                        data2.recycle()
                        reply2.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "waitFor failed: ${e.message}")
                    -1
                }
            } finally {
                watchdog.interrupt()
                try {
                    watchdog.join(500)
                } catch (_: InterruptedException) {
                }
            }

            ShellResult(exitCode, text.trim())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun destroyProcess(processBinder: IBinder) {
        try {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(REMOTE_PROCESS_DESCRIPTOR)
                ShizukuBinderWrapper(processBinder).transact(TXN_PROC_DESTROY, data, reply, 0)
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "destroy of remote process failed: ${e.message}")
        }
    }

    /**
     * Reads a value from the `secure` settings table as the shell user.
     * Useful for the local Bluetooth MAC address, which is normally only
     * visible with the privileged LOCAL_MAC_ADDRESS permission.
     */
    fun readSecureSetting(name: String): String? {
        val outputText = runCommand(arrayOf("settings", "get", "secure", name))
        val value = outputText?.stdout
        if (value == null || value.isEmpty() || value == "null") return null
        return value
    }

    /** Grants a runtime permission via Shizuku without the settings dialog. */
    fun grantRuntimePermission(pkg: String, permission: String): Boolean {
        val outputText = runCommand(arrayOf("pm", "grant", pkg, permission)) ?: return false
        return outputText.exitCode == 0
    }

    /** Enables a screen-overlay (used for the connection popup) without user friction. */
    fun allowSystemAlertWindow(pkg: String): Boolean {
        val outputText = runCommand(arrayOf("appops", "set", pkg, "SYSTEM_ALERT_WINDOW", "allow"))
            ?: return false
        return outputText.exitCode == 0
    }
}
