package io.github.mothlight.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StartupContent(mothlight: Mothlight, modifier: Modifier) {
    val spans by mothlight.startup.spans.collectAsState()
    val rows = remember(spans) { startupRows(spans) }
    var selectedId by remember(mothlight) { mutableStateOf(0L) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.startup_hint), style = MaterialTheme.typography.bodySmall)
        if (rows.isEmpty()) {
            Text(stringResource(Res.string.startup_empty))
        } else {
            val total = rows.last().offsetMillis.toULong()
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.startup_axis), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.duration_ms, "0"), style = MaterialTheme.typography.labelMedium)
                        if (total > 1uL) {
                            Text(stringResource(Res.string.duration_ms, (total / 2uL).toString()),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (total > 0uL) Text(stringResource(Res.string.duration_ms, total.toString()),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.id }) { row ->
                    val selected = selectedId == row.id
                    Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(
                            Modifier.fillMaxWidth().testTag("startup-row:${row.id}")
                                .selectable(selected, onClick = { selectedId = if (selected) 0L else row.id })
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(row.stage, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (row.isLast) stringResource(Res.string.startup_last_mark, row.offsetMillis)
                                else stringResource(Res.string.startup_interval, row.offsetMillis, row.durationMillis),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            StartupTrack(row, selected, total > 0uL)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StartupTrack(row: StartupRow, selected: Boolean, hasElapsedTime: Boolean) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val active = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val padding = 4.dp.toPx()
        val width = (size.width - 2 * padding).coerceAtLeast(0f)
        val center = size.height / 2
        fun x(fraction: Float) = padding + width * fraction
        if (hasElapsedTime) {
            drawLine(track, Offset(x(0f), center), Offset(x(1f), center), strokeWidth = 2.dp.toPx())
            for (tick in listOf(0f, 0.5f, 1f)) {
                drawLine(track, Offset(x(tick), center - 4.dp.toPx()), Offset(x(tick), center + 4.dp.toPx()))
            }
        }
        if (row.startFraction == row.endFraction) {
            drawCircle(active, radius = 3.dp.toPx(), center = Offset(x(row.startFraction), center))
        } else {
            drawLine(active, Offset(x(row.startFraction), center), Offset(x(row.endFraction), center),
                strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
