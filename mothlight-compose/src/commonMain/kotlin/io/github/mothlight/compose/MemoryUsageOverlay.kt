package io.github.mothlight.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mothlight.Mothlight
import io.github.mothlight.memory.*
import io.github.mothlight.compose.resources.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.stringResource

/** Place once in the host layout; removing this composable cancels sampling. */
@Composable
fun MemoryUsageOverlay(
    mothlight: Mothlight,
    modifier: Modifier = Modifier,
    source: MemorySource = PlatformMemorySource,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val enabled by mothlight.memoryOverlay.enabled.collectAsState()
    if (enabled == false) return
    val readings = remember(mothlight, source, dispatcher) {
        observeMemory(mothlight.memoryOverlay, source, dispatcher = dispatcher)
    }
    val snapshot by readings.collectAsState(MemorySnapshot())
    val presentation = remember(snapshot) { memoryPresentation(snapshot) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MemoryBadge(presentation)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryBadge(presentation: MemoryPresentation, modifier: Modifier = Modifier) {
    val color = when (presentation.pressure) {
        MemoryPressure.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        MemoryPressure.ELEVATED -> MaterialTheme.colorScheme.tertiaryContainer
        MemoryPressure.HIGH -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (presentation.pressure) {
        MemoryPressure.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        MemoryPressure.ELEVATED -> MaterialTheme.colorScheme.onTertiaryContainer
        MemoryPressure.HIGH -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier, shape = MaterialTheme.shapes.small, color = color, contentColor = contentColor) {
        FlowRow(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (presentation.rows.isEmpty()) {
                Text(stringResource(Res.string.memory_unavailable), style = MaterialTheme.typography.labelSmall)
            }
            presentation.rows.forEach { row ->
                Text(
                    stringResource(Res.string.memory_metric_value, stringResource(metricLabel(row.metric)), row.value, stringResource(unitLabel(row.unit))),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun metricLabel(metric: MemoryMetric) = when (metric) {
    MemoryMetric.HEAP -> Res.string.memory_heap
    MemoryMetric.HEAP_USAGE -> Res.string.memory_heap_usage
    MemoryMetric.NATIVE_HEAP -> Res.string.memory_native
    MemoryMetric.JVM_NON_HEAP -> Res.string.memory_non_heap
    MemoryMetric.RESIDENT -> Res.string.memory_resident
    MemoryMetric.FOOTPRINT -> Res.string.memory_footprint
}
private fun unitLabel(unit: MemoryUnit) = when (unit) {
    MemoryUnit.MIB -> Res.string.memory_mib
    MemoryUnit.GIB -> Res.string.memory_gib
    MemoryUnit.PERCENT -> Res.string.memory_percent
}

@Preview
@Composable
private fun MemoryBadgePreview() {
    MaterialTheme {
        MemoryBadge(memoryPresentation(MemorySnapshot(MemoryReading.Bytes(400L * 1024 * 1024), MemoryReading.Bytes(512L * 1024 * 1024), MemoryReading.Bytes(64L * 1024 * 1024))))
    }
}
