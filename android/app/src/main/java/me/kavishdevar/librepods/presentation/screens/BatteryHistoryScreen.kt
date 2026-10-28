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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.data.BatteryHistoryStore
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem

private val LeftBatteryColor = Color(0xFF4DA3FF)
private val RightBatteryColor = Color(0xFF4ADE90)
private val CaseBatteryColor = Color(0xFFFFD24A)

/** Time window of the chart. */
private const val CHART_WINDOW_MS = 24L * 60 * 60 * 1000

@Composable
fun BatteryHistoryScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val refreshKey = remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<BatteryHistoryStore.Entry>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey.intValue) {
        entries = BatteryHistoryStore.loadHistory(context)
    }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding =
        if (m3eEnabled) 16.dp else
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding =
        if (m3eEnabled) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    val cardColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "No battery history yet.\n\n" +
                        "Connect your AirPods with PodMate running in the background — " +
                        "battery levels are recorded automatically and will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF221A46), Color(0xFF342E77))
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Legend(entries)
                    Spacer(modifier = Modifier.height(12.dp))
                    BatteryChart(entries)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "24h ago",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "now",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BatteryStatsRow(entries)

            Spacer(modifier = Modifier.height(16.dp))
        }

        val backdrop = rememberLayerBackdrop()
        StyledButton(
            onClick = { showClearDialog = true },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            enabled = entries.isNotEmpty()
        ) {
            Text(text = "Clear battery history", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear battery history?") },
            text = { Text("All recorded AirPods battery levels will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        BatteryHistoryStore.clear(context)
                        refreshKey.intValue++
                        Toast.makeText(context, "Battery history cleared", Toast.LENGTH_SHORT)
                            .show()
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Legend(entries: List<BatteryHistoryStore.Entry>) {
    val last = entries.lastOrNull() ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LegendItem(LeftBatteryColor, "Left", last.left)
        LegendItem(RightBatteryColor, "Right", last.right)
        LegendItem(CaseBatteryColor, "Case", last.case)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 5.dp)
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label + " " + if (level >= 0) "$level%" else "—",
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BatteryStatsRow(entries: List<BatteryHistoryStore.Entry>) {
    val lows = entries.mapNotNull { if (it.left in 0..100) it.left else null }
    val rights = entries.mapNotNull { if (it.right in 0..100) it.right else null }
    val cases = entries.mapNotNull { if (it.case in 0..100) it.case else null }
    if (lows.isEmpty() && rights.isEmpty() && cases.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Highlights",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatLine("Left battery range", rangeLabel(lows))
        StatLine("Right battery range", rangeLabel(rights))
        StatLine("Case battery range", rangeLabel(cases))
    }
}

private fun rangeLabel(values: List<Int>): String {
    if (values.isEmpty()) return "—"
    return "${values.min()}% – ${values.max()}%"
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BatteryChart(entries: List<BatteryHistoryStore.Entry>) {
    val from =
        maxOf((entries.firstOrNull()?.time ?: System.currentTimeMillis()) - CHART_WINDOW_MS, 0L)
    val span = maxOf(System.currentTimeMillis() - from, 1L)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val w = size.width
        val h = size.height
        val padTop = h * 0.08f
        val padBottom = h * 0.10f
        val usableHeight = h - padTop - padBottom

        fun xFor(time: Long): Float =
            ((time - from).coerceAtLeast(0L).toFloat() / span) * w

        fun yFor(level: Int): Float = padTop + (100 - level) / 100f * usableHeight

        val gridColor = Color.White.copy(alpha = 0.16f)
        listOf(0, 25, 50, 75, 100).forEach { level ->
            val y = yFor(level)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        fun computePath(valueFor: (BatteryHistoryStore.Entry) -> Int): Path {
            val path = Path()
            var started = false
            entries.forEach { entry ->
                val level = valueFor(entry)
                if (level in 0..100) {
                    val x = xFor(entry.time)
                    val y = yFor(level)
                    if (started) {
                        path.lineTo(x, y)
                    } else {
                        path.moveTo(x, y)
                        started = true
                    }
                } else {
                    started = false
                }
            }
            return path
        }

        val stroke = Stroke(width = 3f, cap = StrokeCap.Round)

        val series = listOf(
            LeftBatteryColor to { e: BatteryHistoryStore.Entry -> e.left },
            RightBatteryColor to { e: BatteryHistoryStore.Entry -> e.right },
            CaseBatteryColor to { e: BatteryHistoryStore.Entry -> e.case }
        )
        series.forEach { (color, valueFor) ->
            val path = computePath(valueFor)
            drawPath(path, color = color, style = stroke)
        }
    }
}
