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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.CustomEq
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import org.json.JSONArray
import org.json.JSONObject

private const val EQ_PRESETS_PREF_KEY = "eq_presets_json"

private data class EqPreset(
    val name: String,
    val low: Int,
    val mid: Int,
    val high: Int
)

/**
 * A section for the equalizer screen: named presets for the custom 3-band EQ,
 * with file export/import.
 */
@Composable
fun EqPresetsSection(
    currentEq: CustomEq,
    onApply: (low: Int, mid: Int, high: Int) -> Unit
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) return

    val scope = rememberCoroutineScope()
    val refreshKey = remember { mutableIntStateOf(0) }
    var presets by remember { mutableStateOf<List<EqPreset>>(emptyList()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var presetPendingDelete by remember { mutableStateOf<EqPreset?>(null) }

    LaunchedEffect(refreshKey.intValue) {
        presets = loadEqPresets(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val array = JSONArray()
                        presetList(context).forEach { preset ->
                            array.put(
                                JSONObject()
                                    .put("name", preset.name)
                                    .put("low", preset.low)
                                    .put("mid", preset.mid)
                                    .put("high", preset.high)
                            )
                        }
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(array.toString(2).toByteArray())
                        } != null
                    } catch (e: Exception) {
                        false
                    }
                }
                Toast.makeText(
                    context,
                    if (ok) "EQ presets exported" else "Export failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val text = stream.bufferedReader().readText()
                            val array = JSONArray(text)
                            val incoming = ArrayList<EqPreset>()
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i) ?: continue
                                val preset = EqPreset(
                                    name = obj.optString("name", "Preset"),
                                    low = obj.optInt("low", 50).coerceIn(0, 100),
                                    mid = obj.optInt("mid", 50).coerceIn(0, 100),
                                    high = obj.optInt("high", 50).coerceIn(0, 100)
                                )
                                incoming.add(preset)
                            }
                            if (incoming.isEmpty()) null else mergePresets(context, incoming)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (imported != null) {
                    refreshKey.intValue++
                    Toast.makeText(context, "EQ presets imported", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    StyledList(
        title = stringResource(R.string.eq_presets),
        description = stringResource(R.string.eq_presets_description)
    ) {
        StyledListItem(
            name = stringResource(R.string.eq_save_current),
            onClick = {
                saveName = ""
                showSaveDialog = true
            }
        )

        presets.forEach { preset ->
            StyledListItem(
                name = preset.name,
                description = "Low ${preset.low} · Mid ${preset.mid} · High ${preset.high}",
                onClick = {
                    onApply(preset.low, preset.mid, preset.high)
                    Toast.makeText(
                        context,
                        context.getString(R.string.eq_preset_applied, preset.name),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                trailingContent = {
                    androidx.compose.material3.IconButton(onClick = {
                        presetPendingDelete = preset
                    }) {
                        Text(
                            text = "✕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        StyledListItem(
            name = stringResource(R.string.eq_export_presets),
            onClick = { exportLauncher.launch("podmate-eq-presets.json") },
            enabled = presets.isNotEmpty()
        )
        StyledListItem(
            name = stringResource(R.string.eq_import_presets),
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.eq_save_current)) },
            text = {
                Column {
                    Text(stringResource(R.string.eq_save_current_description))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = saveName.isNotBlank(),
                    onClick = {
                        showSaveDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                upsertPreset(
                                    context,
                                    EqPreset(
                                        name = saveName.trim(),
                                        low = currentEq.low,
                                        mid = currentEq.mid,
                                        high = currentEq.high
                                    )
                                )
                            }
                            refreshKey.intValue++
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val pendingDelete = presetPendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { presetPendingDelete = null },
            title = { Text(stringResource(R.string.eq_delete_preset)) },
            text = {
                Text(
                    stringResource(
                        R.string.eq_delete_preset_confirmation,
                        pendingDelete.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        presetPendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                deletePreset(context, pendingDelete.name)
                            }
                            refreshKey.intValue++
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { presetPendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun pref(context: android.content.Context) =
    context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

private fun presetList(context: android.content.Context): List<EqPreset> {
    val raw = pref(context).getString(EQ_PRESETS_PREF_KEY, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            EqPreset(
                name = obj.optString("name", "Preset"),
                low = obj.optInt("low", 50),
                mid = obj.optInt("mid", 50),
                high = obj.optInt("high", 50)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun savePresets(context: android.content.Context, presets: List<EqPreset>) {
    val array = JSONArray()
    presets.forEach {
        array.put(
            JSONObject()
                .put("name", it.name)
                .put("low", it.low)
                .put("mid", it.mid)
                .put("high", it.high)
        )
    }
    pref(context).edit().putString(EQ_PRESETS_PREF_KEY, array.toString()).apply()
}

private fun loadEqPresets(context: android.content.Context): List<EqPreset> =
    presetList(context)

private fun upsertPreset(context: android.content.Context, preset: EqPreset) {
    val presets = presetList(context).filterNot { it.name.equals(preset.name, ignoreCase = true) }
        .toMutableList()
    presets.add(preset)
    savePresets(context, presets)
}

/** Merges an imported list into the saved ones (existing names are replaced). */
private fun mergePresets(
    context: android.content.Context,
    incoming: List<EqPreset>
): List<EqPreset> {
    val base = presetList(context).toMutableList()
    incoming.forEach { preset ->
        base.removeAll { it.name.equals(preset.name, ignoreCase = true) }
        base.add(preset)
    }
    savePresets(context, base)
    return base
}

private fun deletePreset(context: android.content.Context, name: String) {
    val presets = presetList(context).filterNot { it.name == name }
    savePresets(context, presets)
}
