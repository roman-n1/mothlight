package io.github.mothlight.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.mothlight.*
import io.github.mothlight.compose.*
import kotlin.test.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class LogsInspectorUiTest {
    @Test fun filtersCopiesAndExpandedMessagesSurviveLeavingTheTool() = runDesktopComposeUiTest(width = 420, height = 900) {
        val mothlight = Mothlight().apply {
            setEnabled(true)
            logs.record(LogEntry("1", 1000, LogLevel.INFO, "Cache", "Ready"))
            logs.record(LogEntry("2", 2000, LogLevel.WARN, "Cache", "Read failed"))
            logs.record(LogEntry("3", 3000, LogLevel.ERROR, "Cache", "Write failed\nRetry with cached data\nDiagnostic token=***"))
            logs.record(LogEntry("4", 4000, LogLevel.ERROR, "Network", "Request failed"))
            network.recordStart("GET", "https://example.com", emptyMap(), CapturedBody.NotCaptured, 0)
        }
        val state = MothlightPanelState().apply { selectTool("logs") }
        val clipboard = LogTestClipboard()
        setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) {
            DemoTheme(true) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) }
        } }
        onNodeWithTag("logs-search").performTextInput("FAILED")
        onNodeWithTag("logs-level-ERROR").assertIsDisplayed().performClick()
        onNodeWithTag("logs-tags").performClick()
        onNodeWithTag("logs-tag:Cache").performClick()
        onNodeWithTag("log-row:3").assertIsDisplayed()
        onNodeWithTag("log-row:2").assertDoesNotExist()
        onNodeWithTag("log-row:4").assertDoesNotExist()
        onNodeWithTag("logs-copy-filtered").performClick()
        runOnIdle { assertEquals("[3000] ERROR Cache\nWrite failed\nRetry with cached data\nDiagnostic token=***", clipboard.contents.text) }
        onNodeWithTag("log-row:3").performClick()
        onNodeWithTag("logs-follow").assertIsOff()
        onNodeWithTag("logs-copy-entry:3").performScrollTo().performClick()
        runOnIdle { assertTrue(clipboard.contents.text.contains("Retry with cached data")); assertTrue(mothlight.enabled.value) }
        runOnIdle { state.selectTool("http") }
        onNodeWithTag("logs-content").assertDoesNotExist()
        runOnIdle { state.selectTool("logs") }
        onNodeWithTag("logs-search").assertTextContains("FAILED")
        onNodeWithTag("logs-level-ERROR").assertIsSelected()
        onNodeWithTag("logs-tags").assertTextContains("Cache", substring = true)
        onNodeWithTag("logs-copy-entry:3").assertIsDisplayed()
        onNodeWithTag("logs-search").performTextReplacement("missing-message")
        onNodeWithTag("logs-copy-filtered").assertIsNotEnabled()
        onNodeWithTag("logs-clear").performClick()
        runOnIdle { assertTrue(mothlight.logs.items.value.isEmpty()); assertEquals(1, mothlight.network.transactions.value.size) }
    }

    @Test fun pausingPreservesReadingPositionCountsNewMatchesAndResumesAtNewest() = runDesktopComposeUiTest(width = 1000, height = 850) {
        val mothlight = Mothlight(capacity = 100).apply {
            setEnabled(true)
            repeat(60) { logs.record(LogEntry("$it", it * 1000L, LogLevel.INFO, "Sync", "Message $it")) }
        }
        val state = MothlightPanelState().apply { selectTool("logs") }
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
        onNodeWithTag("logs-follow").performClick().assertIsOff()
        onNodeWithTag("logs-list").performScrollToKey("20")
        val before = onNodeWithTag("log-row:20").fetchSemanticsNode().boundsInRoot.top
        runOnIdle { repeat(3) { mothlight.logs.record(LogEntry("new-$it", 100000L + it, LogLevel.INFO, "Sync", "New message $it")) } }
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertTextContains("3", substring = true)
        assertEquals(before, onNodeWithTag("log-row:20").fetchSemanticsNode().boundsInRoot.top)
        runOnIdle { state.selectTool("http") }
        onNodeWithTag("logs-content").assertDoesNotExist()
        runOnIdle { mothlight.logs.record(LogEntry("hidden", 101000, LogLevel.INFO, "Sync", "Arrived offscreen")); state.selectTool("logs") }
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertTextContains("4", substring = true)
        assertEquals(before, onNodeWithTag("log-row:20").fetchSemanticsNode().boundsInRoot.top)
        onNodeWithTag("logs-follow").performClick().assertIsOn()
        onNodeWithTag("log-row:hidden").assertIsDisplayed()
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertDoesNotExist()
        runOnIdle { assertTrue(mothlight.enabled.value); assertEquals(64, mothlight.logs.items.value.size) }
        onNodeWithTag("logs-list").performTouchInput { swipeUp() }
        onNodeWithTag("logs-follow").assertIsOff()
    }

    @Test fun evictedRowsAreNotRetainedByPauseOrExpandedSelection() = runDesktopComposeUiTest(width = 900, height = 800) {
        val mothlight = Mothlight(capacity = 2).apply {
            setEnabled(true)
            logs.record(LogEntry("old", 0, LogLevel.ERROR, "Worker", "Old failure"))
        }
        val state = MothlightPanelState().apply { selectTool("logs") }
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
        onNodeWithTag("log-row:old").performClick()
        onNodeWithTag("logs-follow").assertIsOff()
        runOnIdle { repeat(5) { mothlight.logs.record(LogEntry("new-$it", it.toLong(), LogLevel.INFO, "Worker", "New $it")) } }
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertTextContains("2", substring = true)
        onNodeWithTag("log-row:old").assertDoesNotExist()
        onNodeWithTag("logs-copy-entry:old").assertDoesNotExist()
        runOnIdle { mothlight.clear() }
        onNodeWithTag("logs-pending", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("logs-list").assertDoesNotExist()
    }
}

@Suppress("DEPRECATION")
private class LogTestClipboard : ClipboardManager {
    var contents = AnnotatedString("")
    override fun setText(annotatedString: AnnotatedString) { contents = annotatedString }
    override fun getText() = contents
}
