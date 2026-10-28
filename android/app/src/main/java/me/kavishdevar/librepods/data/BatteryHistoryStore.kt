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

package me.kavishdevar.librepods.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a lightweight history of AirPods battery levels over time so they can
 * be graphed in the Battery History screen.
 *
 * Entries are stored as a JSON array in a dedicated shared preferences file.
 * -1 level means "unknown" (e.g. component not present/disconnected).
 */
object BatteryHistoryStore {
    private const val TAG = "BatteryHistoryStore"
    private const val PREFS_FILE = "battery_history"
    private const val KEY_ENTRIES = "entries"

    /** At most this many entries are kept (~ days, depending on update frequency). */
    private const val MAX_ENTRIES = 800

    /** Don't write more often than this; AirPods update battery data frequently. */
    private const val MIN_RECORD_INTERVAL_MS = 45_000L

    /** How long an unchanged snapshot is logged after the last one (to avoid holes). */
    private const val HEARTBEAT_INTERVAL_MS = 3 * 60_000L

    data class Entry(
        val time: Long,
        val left: Int,
        val right: Int,
        val case: Int
    )

    @Volatile
    private var lastRecordedAt = 0L
    private var lastSavedJson: String? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun entryJson(entry: Entry): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"t\":").append(entry.time).append(',')
        sb.append("\"l\":").append(entry.left).append(',')
        sb.append("\"r\":").append(entry.right).append(',')
        sb.append("\"c\":").append(entry.case)
        sb.append('}')
        return sb.toString()
    }

    private fun parseEntry(json: JSONObject): Entry {
        return Entry(
            time = json.optLong("t", 0L),
            left = json.optInt("l", -1),
            right = json.optInt("r", -1),
            case = json.optInt("c", -1)
        )
    }

    private fun loadEntries(context: Context): MutableList<Entry> {
        val list = mutableListOf<Entry>()
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return list
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(parseEntry(obj))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse battery history, discarding: $e")
        }
        return list
    }

    private fun saveEntries(context: Context, entries: List<Entry>) {
        try {
            val array = JSONArray()
            entries.forEach { array.put(JSONObject(entryJson(it))) }
            val json = array.toString()
            if (json == lastSavedJson) return
            prefs(context).edit().putString(KEY_ENTRIES, json).apply()
            lastSavedJson = json
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save battery history: $e")
        }
    }

    fun levelFor(batteries: List<Battery>, component: Int): Int {
        return batteries.firstOrNull { it.component == component }?.level
            ?.takeIf { it in 0..100 } ?: -1
    }

    /**
     * Called from AirPodsService whenever battery data updates. Writes are
     * throttled so preference I/O stays cheap.
     */
    fun record(context: Context, batteries: List<Battery>) {
        try {
            val left = levelFor(batteries, BatteryComponent.LEFT)
            val right = levelFor(batteries, BatteryComponent.RIGHT)
            val case = levelFor(batteries, BatteryComponent.CASE)
            if (left < 0 && right < 0 && case < 0) return

            val now = System.currentTimeMillis()
            val entries = loadEntries(context)
            val last = entries.lastOrNull()

            val unchanged = last != null &&
                last.left == left && last.right == right && last.case == case

            if (unchanged) {
                if (now - last.time >= HEARTBEAT_INTERVAL_MS) {
                    entries.add(Entry(now, left, right, case))
                } else {
                    return
                }
            } else {
                if (now - lastRecordedAt < MIN_RECORD_INTERVAL_MS && last != null) {
                    return
                }
                entries.add(Entry(now, left, right, case))
            }
            lastRecordedAt = now

            // Trim old data: keep the newest MAX_ENTRIES
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }

            saveEntries(context, entries)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record battery history entry: $e")
        }
    }

    fun loadHistory(context: Context): List<Entry> {
        return try {
            loadEntries(context).sortedBy { it.time }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        try {
            prefs(context).edit().clear().apply()
            lastSavedJson = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear battery history: $e")
        }
    }
}
