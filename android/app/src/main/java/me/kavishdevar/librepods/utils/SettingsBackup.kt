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

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Exports/imports all of the app's `shared_prefs` XML files (settings,
 * EQ presets, etc.) through the Storage Access Framework.
 */
object SettingsBackup {
    private const val TAG = "SettingsBackup"

    private const val FORMAT_VERSION = 1

    /** Preference files that hold device data rather than settings. */
    private val excludedPrefs = setOf(
        "battery_history.xml"
    )

    data class BackupResult(val succeeded: Boolean, val fileCount: Int)

    fun backup(context: Context, uri: Uri): BackupResult {
        val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
        if (!prefsDir.exists()) return BackupResult(false, 0)
        val files = prefsDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".xml") && !excludedPrefs.contains(it.name)
        } ?: emptyList()

        val json = JSONObject()
        json.put("format_version", FORMAT_VERSION)
        json.put("app", "PodMate")
        for (file in files) {
            json.put(file.name, file.readText())
        }

        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toString(2).toByteArray())
            } ?: return BackupResult(false, 0)
            Log.d(TAG, "Backed up ${files.size} preference files")
            BackupResult(true, files.size)
        } catch (e: Exception) {
            Log.w(TAG, "Backup failed: $e")
            BackupResult(false, 0)
        }
    }

    fun restore(context: Context, uri: Uri): BackupResult {
        val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                JSONObject(stream.bufferedReader().readText())
            } ?: return BackupResult(false, 0)

            val app = json.optString("app", "")
            if (app != "PodMate") {
                return BackupResult(false, 0)
            }

            if (!prefsDir.exists()) prefsDir.mkdirs()

            var restored = 0
            for (name in json.keySet()) {
                if (name == "format_version" || name == "app") continue
                if (!name.endsWith(".xml")) continue
                File(prefsDir, name).writeText(json.optString(name))
                restored++
            }

            // Reload the restored files: prefs are cached per-process, so the
            // app should be restarted to pick them up (see [restartApp]).
            Log.d(TAG, "Restored $restored preference files")
            BackupResult(true, restored)
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed: $e")
            BackupResult(false, 0)
        }
    }

    /**
     * Restored preferences are cached by the process, so the app has to start
     * fresh. Schedules a relaunch and kills the current process.
     */
    fun restartApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        context.startActivity(intent)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            {
                                android.os.Process.killProcess(android.os.Process.myPid())
                            },
                            150L
                        )
                    } catch (e: Exception) {
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        } catch (e: Exception) {
            Log.w(TAG, "App restart failed: $e")
        }
    }
}
