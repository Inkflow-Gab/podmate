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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.BuildConfig
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub repository for a newer release of the app.
 *
 * The repo publishes releases like `v1.1.0` containing an APK named
 * `PodMate-v1.1.0-<versionCode>-release.apk`. The version code (if present in
 * the asset name) is preferred for comparison, since it is the most reliable.
 */
object AppRelease {
    private const val TAG = "AppRelease"

    const val REPO_OWNER = "Inkflow-Gab"
    const val REPO_NAME = "podmate"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    data class LatestRelease(
        /** e.g. `v1.1.0` */
        val tagName: String,
        /** e.g. `1.1.0` (leading `v` stripped) */
        val versionName: String,
        /** Version code parsed from the APK asset name, or -1 if unknown. */
        val versionCode: Long,
        /** Direct download url of the release APK asset (browser-downloadable). */
        val apkUrl: String?,
        /** Human friendly release page url. */
        val releaseUrl: String
    )

    data class UpdateCheckResult(
        val release: LatestRelease?,
        /** null = no internet/parsing failure, false = up to date, true = update available */
        val updateAvailable: Boolean?
    )

    /** Parses "v1.2.3" / "1.2.3" into [1, 2, 3] or null. */
    fun parseVersionBits(version: String?): IntArray? {
        if (version == null) return null
        val v = version.trim().removePrefix("v")
        val match = Regex("^\\s*(\\d+)\\.(\\d+)\\.(\\d+)").find(v) ?: return null
        return match.groupValues.let { intArrayOf(it[1].toInt(), it[2].toInt(), it[3].toInt()) }
    }

    fun currentVersionName(): String {
        return BuildConfig.VERSION_NAME
            .removeSuffix("-debug")
            .removeSuffix("-play")
    }

    fun compareBits(a: IntArray, b: IntArray): Int {
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return 0
    }

    private fun isNewer(release: LatestRelease): Boolean {
        val currentBits = parseVersionBits(currentVersionName()) ?: return false
        val releaseBits = parseVersionBits(release.versionName) ?: return false
        val tagCompare = compareBits(releaseBits, currentBits)
        if (tagCompare != 0) return tagCompare > 0
        // Same version name – fall back to version code comparison e.g. rebuilds
        if (release.versionCode > 0) {
            return release.versionCode > BuildConfig.VERSION_CODE
        }
        return false
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(LATEST_RELEASE_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "PodMate/${BuildConfig.VERSION_NAME}")
            try {
                if (connection.responseCode != 200) {
                    Log.w(TAG, "Latest release check failed: HTTP ${connection.responseCode}")
                    return@withContext UpdateCheckResult(null, null)
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val releaseBits = parseVersionBits(tagName)
                // Only consider stable releases, ignore prerelease/nightly builds
                if (releaseBits == null || json.optBoolean("prerelease", true)) {
                    return@withContext UpdateCheckResult(null, null)
                }

                var apkUrl: String? = null
                var versionCode = -1L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith("release.apk")) {
                            apkUrl = asset.optString("browser_download_url", "")
                                .ifEmpty { null }
                            val codeMatch = Regex("\\-(\\d+)-release\\.apk$").find(name)
                            if (codeMatch != null) {
                                versionCode = codeMatch.groupValues[1].toLongOrNull() ?: -1L
                            }
                            break
                        }
                    }
                }
                val release = LatestRelease(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    versionCode = versionCode,
                    apkUrl = apkUrl,
                    releaseUrl = json.optString("html_url", RELEASES_PAGE_URL)
                )
                UpdateCheckResult(release, isNewer(release))
            } finally {
                connection.disconnect()
            }
        } catch (_: FileNotFoundException) {
            // No published release yet
            UpdateCheckResult(null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: $e")
            UpdateCheckResult(null, null)
        }
    }
}
