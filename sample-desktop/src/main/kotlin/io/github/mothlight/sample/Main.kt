package io.github.mothlight.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.mothlight.compose.*
import io.github.mothlight.sample.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Mothlight") {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFD9C4FF), secondary = Color(0xFFB6E4D0))) {
            val demo = remember { DemoSession() }
            val scope = rememberCoroutineScope()
            DisposableEffect(demo) { onDispose { demo.close() } }
            val tools = remember(demo) {
                MothlightTools(defaultMothlightTools(demo.mothlight).entries + databaseTool(demo.databaseSource) + MothlightTool(
                    id = "example-plugin",
                    title = { stringResource(Res.string.custom_tool) },
                    content = { modifier ->
                        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(Res.string.plugin_description), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { demo.event() }) { Text(stringResource(Res.string.emit_event)) }
                        }
                    },
                ))
            }
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Mothlight", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(Res.string.tagline), color = MaterialTheme.colorScheme.onBackground)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { scope.launch { demo.request() } }) { Text(stringResource(Res.string.mock_request)) }
                    OutlinedButton(onClick = { scope.launch { demo.addRowAndFrame() } }) { Text(stringResource(Res.string.add_row_frame)) }
                    OutlinedButton(onClick = { diagnosticScenario(demo.mothlight) }) { Text(stringResource(Res.string.diagnostic_scenario)) }
                    OutlinedButton(onClick = demo::log) { Text(stringResource(Res.string.add_log)) }
                }
                MemoryUsageOverlay(demo.mothlight)
                MothlightPanel(demo.mothlight, Modifier.fillMaxWidth().weight(1f), tools)
            }
        }
    }
}
