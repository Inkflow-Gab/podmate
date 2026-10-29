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

package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.utils.AppRelease
import me.kavishdevar.librepods.utils.SettingsBackup
import me.kavishdevar.librepods.utils.ShizukuState
import me.kavishdevar.librepods.utils.XposedState

/**
 * PodMate-specific settings: app updates, battery history, settings backup,
 * Shizuku helpers and connection diagnostics.
 */
@Composable
fun PodmateSettingsSection(
    navigateToBatteryHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateResult by remember { mutableStateOf<AppRelease.UpdateCheckResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val checkTrigger = remember { mutableIntStateOf(0) }

    // Compose mirrors of the Shizuku state so the UI reacts to grants.
    val shizukuAvailable = remember { mutableStateOf(ShizukuState.isServerAvailable) }
    val shizukuGranted = remember { mutableStateOf(ShizukuState.isGranted) }
    val diagText = remember { mutableStateOf<String?>(null) }
    val refreshShizuku = {
        shizukuAvailable.value = ShizukuState.isServerAvailable
        shizukuGranted.value = ShizukuState.isGranted
    }

    LaunchedEffect(checkTrigger.intValue) {
        isChecking = true
        updateResult = AppRelease.checkForUpdate()
        isChecking = false
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    SettingsBackup.backup(context, uri)
                }
                Toast.makeText(
                    context,
                    if (result.succeeded) "Settings backed up" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    SettingsBackup.restore(context, uri)
                }
                if (result.succeeded) {
                    Toast.makeText(
                        context,
                        "Settings restored — restarting app…",
                        Toast.LENGTH_LONG
                    ).show()
                    withContext(Dispatchers.Main) {
                        delay(800)
                        SettingsBackup.restartApp(context)
                    }
                } else {
                    Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val updateDescription = when {
        isChecking -> stringResource(R.string.update_checking)
        updateResult == null -> stringResource(R.string.update_tap_to_check)
        updateResult?.updateAvailable == null -> stringResource(R.string.update_check_failed)
        updateResult?.updateAvailable == false ->
            stringResource(
                R.string.update_up_to_date,
                AppRelease.currentVersionName()
            )
        else -> stringResource(
            R.string.update_available,
            updateResult?.release?.versionName ?: ""
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    StyledList(title = stringResource(R.string.podmate_section)) {
        StyledListItem(
            name = stringResource(R.string.app_updates),
            description = updateDescription,
            onClick = {
                val available = updateResult?.updateAvailable == true
                val target = updateResult?.release?.apkUrl ?: updateResult?.release?.releaseUrl
                if (available && target != null) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, target.toUri())
                    )
                } else {
                    checkTrigger.intValue++
                }
            }
        )

        StyledListItem(
            name = stringResource(R.string.battery_history),
            description = stringResource(R.string.battery_history_description),
            onClick = navigateToBatteryHistory
        )

        StyledListItem(
            name = stringResource(R.string.backup_settings),
            description = stringResource(R.string.backup_settings_description),
            onClick = { backupLauncher.launch("podmate-settings.json") }
        )

        StyledListItem(
            name = stringResource(R.string.restore_settings),
            description = stringResource(R.string.restore_settings_description),
            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
        )
    }

    if (updateResult?.updateAvailable == true) {
        Spacer(modifier = Modifier.height(12.dp))
        val backdrop = rememberLayerBackdrop()
        StyledButton(
            onClick = {
                val target = updateResult?.release?.apkUrl
                    ?: updateResult?.release?.releaseUrl
                    ?: AppRelease.RELEASES_PAGE_URL
                context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
            },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.download_update))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    StyledList(title = stringResource(R.string.shizuku_section)) {
        StyledListItem(
            name = stringResource(R.string.shizuku_access),
            description = when {
                !shizukuAvailable.value -> stringResource(R.string.shizuku_not_available)
                !shizukuGranted.value -> stringResource(R.string.shizuku_tap_to_grant)
                else -> stringResource(R.string.shizuku_active)
            },
            onClick = {
                refreshShizuku()
                when {
                    !ShizukuState.isServerAvailable -> {
                        val launch =
                            context.packageManager.getLaunchIntentForPackage(ShizukuState.SHIZUKU_PACKAGE)
                        if (launch != null) {
                            context.startActivity(launch)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.shizuku_install_hint),
                                Toast.LENGTH_LONG
                            ).show()
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://shizuku.rikka.app/download/".toUri()
                                )
                            )
                        }
                    }

                    !ShizukuState.isGranted -> ShizukuState.requestPermission()
                    else -> Toast.makeText(
                        context,
                        context.getString(R.string.shizuku_already_granted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        StyledListItem(
            name = stringResource(R.string.shizuku_grant_permissions),
            description = stringResource(R.string.shizuku_grant_permissions_description),
            onClick = {
                scope.launch(Dispatchers.IO) {
                    val pkg = context.packageName
                    val grantedConnect = ShizukuState.grantRuntimePermission(
                        pkg, "android.permission.BLUETOOTH_CONNECT"
                    )
                    val grantedNotifications = ShizukuState.grantRuntimePermission(
                        pkg, Manifest.permission.POST_NOTIFICATIONS
                    )
                    withContext(Dispatchers.Main) {
                        refreshShizuku()
                        Toast.makeText(
                            context,
                            when {
                                !ShizukuState.isGranted ->
                                    context.getString(R.string.shizuku_not_granted_yet)
                                grantedConnect && grantedNotifications ->
                                    context.getString(R.string.shizuku_permissions_granted)
                                else ->
                                    context.getString(R.string.shizuku_permissions_partial)
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

        StyledListItem(
            name = stringResource(R.string.shizuku_enable_popup),
            description = stringResource(R.string.shizuku_enable_popup_description),
            onClick = {
                scope.launch(Dispatchers.IO) {
                    val ok = ShizukuState.allowSystemAlertWindow(context.packageName)
                    withContext(Dispatchers.Main) {
                        refreshShizuku()
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.shizuku_popup_enabled)
                            else context.getString(R.string.shizuku_grant_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    StyledList(title = stringResource(R.string.troubleshooting_section)) {
        StyledListItem(
            name = stringResource(R.string.connection_diagnostics),
            description = diagText.value
                ?: stringResource(R.string.connection_diagnostics_description),
            onClick = {
                refreshShizuku()
                diagText.value = collectConnectionDiagnostics(context)
            }
        )
    }
}

/**
 * Builds a human-readable summary of everything that matters for
 * detecting AirPods and talking to them — useful when "the app
 * doesn't detect" on a specific phone.
 */
private fun collectConnectionDiagnostics(context: Context): String {
    try {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lines = mutableListOf<String>()

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        lines += when {
            adapter == null -> "Bluetooth: not available"
            adapter.isEnabled -> "Bluetooth: on"
            else -> "Bluetooth: off — turn it on"
        }

        val connectPermission =
            context.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
        val hasConnect = connectPermission == PackageManager.PERMISSION_GRANTED
        lines += if (hasConnect) {
            "Nearby connect permission: granted"
        } else {
            "Nearby connect permission: missing → open the app's onboarding"
        }

        val savedMac = prefs.getString("mac_address", "") ?: ""
        val bondedAirPods = if (hasConnect) {
            try {
                adapter?.bondedDevices?.any { device ->
                    device.name.contains("airpods", ignoreCase = true) ||
                            device.address.equals(savedMac, ignoreCase = true)
                } ?: false
            } catch (e: Exception) {
                false
            }
        } else false
        lines += if (bondedAirPods) {
            "AirPods bonded: yes"
        } else {
            "AirPods bonded: no/unknown → triple-press the case lid, connect in Settings, then reopen PodMate"
        }

        val channelOk = prefs.getBoolean("connection_successful", false)
        lines += if (channelOk) {
            "Data channel: worked before — reconnect AirPods to refresh"
        } else {
            "Data channel: not established yet — ANC/battery may need root/Xposed on this Android"
        }

        lines += when {
            XposedState.isAvailable -> "Privileges: Xposed hook active"
            ShizukuState.isGranted -> "Privileges: Shizuku connected"
            else -> "Privileges: none (Shizuku optional, root optional)"
        }

        return lines.joinToString("\n")
    } catch (e: Exception) {
        return "Diagnostics failed: ${e.message}"
    }
}
