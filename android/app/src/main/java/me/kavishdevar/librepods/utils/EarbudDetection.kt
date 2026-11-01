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

/**
 * Name-based earbud recognition that needs no permissions and no root.
 *
 * Plenty of "AirPods" bought online are clones that keep an Apple-ish name
 * ("AirPods Pro", "AirPods 3", "TWS AirPods"), and many real AirPods arrive
 * renamed by their owner. Matching a few well-known words catches both, while
 * keeping ordinary speakers/headsets out unless the user explicitly opts in
 * with the `generic_earbuds_mode` switch.
 */
object EarbudDetection {
    private val AIRPODS_WORDS = listOf(
        "airpods", "airpod", "air pods", "superpods", "pods pro", "pods max", "pod pro"
    )

    /** True when [name] looks like AirPods (real or clone). */
    fun nameLooksLikeAirPods(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.lowercase()
        return AIRPODS_WORDS.any { normalized.contains(it) }
    }

    /**
     * "Treat any connected Bluetooth earbuds as AirPods": lets PodMate show
     * battery/popups for earbuds that don't advertise anything Apple-flavoured.
     */
    fun genericModeEnabled(prefs: android.content.SharedPreferences?): Boolean =
        prefs?.getBoolean("generic_earbuds_mode", false) == true
}
