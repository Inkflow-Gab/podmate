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

import android.content.Intent
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

/**
 * PodMate-specific settings: app updates, battery history, settings backup.
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
}
