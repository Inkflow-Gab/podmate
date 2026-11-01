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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.net.NetworkInterface

/**
 * Finds this phone's own Bluetooth MAC address **without root**.
 *
 * Upstream LibrePods shelled out to `su -c "settings get secure bluetooth_address"`
 * which makes the phone show a root prompt as soon as AirPods connect (and does
 * nothing at all on a non-rooted phone — the app just hung for a second).
 * PodMate never calls `su`. The value is only needed for a handful of Apple
 * protocol packets (TiPi / audio-source / connection-ownership), so when it
 * really cannot be read we simply return an empty string and those few packets
 * are skipped; everything else keeps working.
 *
 * Order of preference, cheapest and most reliable first:
 *  1. A value we already resolved and cached in shared preferences.
 *  2. The privileged `LOCAL_MAC_ADDRESS` permission (root-module / system install).
 *  3. Shizuku (shell, no root) reading `settings get secure bluetooth_address`.
 *  4. Reflection on `BluetoothAdapter.getAddress()` (blocked on most ROMs).
 *  5. `Settings.Secure`.
 *  6. The `bt-pan` network interface (some OEM ROMs expose the BD_ADDR there).
 */
object LocalMacResolver {
    private const val TAG = "LocalMacResolver"
    private const val PREF_KEY = "self_mac_address"

    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    fun isValidMac(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        if (!MAC_REGEX.matches(value)) return false
        // "02:00:00:00:00:00" is the well-known placeholder
        return !value.equals("02:00:00:00:00:00", ignoreCase = true)
    }

    /**
     * Resolves the local Bluetooth MAC address, or an empty string when it is
     * genuinely unavailable. Never blocks for more than a couple of seconds and
     * never asks for root.
     */
    @SuppressLint("HardwareIds")
    fun resolve(context: Context, prefs: SharedPreferences? = null): String {
        prefs?.getString(PREF_KEY, null)?.let {
            if (isValidMac(it)) {
                Log.d(TAG, "Using cached local MAC")
                return it
            }
        }

        val adapter = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } catch (e: Exception) {
            Log.w(TAG, "No Bluetooth adapter: ${e.message}")
            null
        }

        // 1. Privileged permission (system app / root module installed as system app).
        if (context.checkSelfPermission("android.permission.LOCAL_MAC_ADDRESS") ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                adapter?.address?.let {
                    if (isValidMac(it)) {
                        Log.d(TAG, "Local MAC from LOCAL_MAC_ADDRESS permission")
                        return it
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "adapter.address failed: ${e.message}")
            }
        }

        // 2. Shizuku, which runs as the shell user (no root needed).
        if (ShizukuState.isGranted) {
            try {
                ShizukuState.readSecureSetting("bluetooth_address")?.let {
                    if (isValidMac(it)) {
                        Log.d(TAG, "Local MAC from Shizuku")
                        return it
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku MAC lookup failed: ${e.message}")
            }
        }

        // 3. Reflection — works on a few ROMs, harmless everywhere else.
        if (adapter != null) {
            try {
                val value = adapter.javaClass.getMethod("getAddress").invoke(adapter) as? String
                if (isValidMac(value)) {
                    Log.d(TAG, "Local MAC from BluetoothAdapter.getAddress()")
                    return value
                }
            } catch (_: Exception) {
                // Hidden / blocked by the platform — expected on stock Android.
            }
        }

        // 4. Settings.Secure, readable on some OEM builds.
        try {
            val value = Settings.Secure.getString(context.contentResolver, "bluetooth_address")
            if (isValidMac(value)) {
                Log.d(TAG, "Local MAC from Settings.Secure")
                return value
            }
        } catch (e: Exception) {
            Log.w(TAG, "Settings.Secure MAC lookup failed: ${e.message}")
        }

        // 5. bt-pan interface (the Bluetooth PAN adapter carries the BD_ADDR).
        try {
            val enumeration = NetworkInterface.getNetworkInterfaces()
            val interfaces = enumeration?.let { java.util.Collections.list(it) }.orEmpty()
            for (networkInterface in interfaces) {
                if (!networkInterface.name.startsWith("bt")) continue
                val hardwareAddress = networkInterface.hardwareAddress ?: continue
                val formatted = hardwareAddress.joinToString(":") { "%02X".format(it) }
                if (isValidMac(formatted)) {
                    Log.d(TAG, "Local MAC from interface ${networkInterface.name}")
                    return formatted
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetworkInterface MAC lookup failed: ${e.message}")
        }

        Log.d(TAG, "Local MAC unavailable — continuing without it (no root required)")
        return ""
    }
}
