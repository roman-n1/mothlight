package io.github.mothlight.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.mothlight.*
import io.github.mothlight.compose.*
import kotlin.test.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class HttpInspectorUiTest {
    @Test fun jsonNodesFoldIndependentlyAndRetainTheirStatePerRequest() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val mothlight = reviewFixture()
        val state = MothlightPanelState()
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
        onNodeWithTag("http-row-3").performClick()
        onNodeWithTag("http-tab-Response").performClick()
        onNodeWithTag("json-node:/items/0").assertDoesNotExist()
        onNodeWithTag("json-node:/items").performScrollTo().performClick()
        onNodeWithTag("json-node:/items/0").performScrollTo().performClick()
        onNodeWithTag("json-node:/items/0/title").performScrollTo().assertIsDisplayed()
        onNodeWithTag("json-node:/items").performScrollTo().performClick()
        onNodeWithTag("json-node:/items/0").assertDoesNotExist()
        onNodeWithTag("http-tab-Request").performClick()
        onNodeWithTag("json-node:/items").assertDoesNotExist()
        onNodeWithTag("http-tab-Response").performClick()
        onNodeWithTag("json-node:/items/0").assertDoesNotExist()
        onNodeWithTag("json-node:/items").performScrollTo().performClick()
        onNodeWithTag("json-node:/items/0/title").performScrollTo().assertIsDisplayed()
        runOnIdle { state.selectTool("logs") }
        onNodeWithTag("http-details").assertDoesNotExist()
        runOnIdle { state.selectTool("http") }
        onNodeWithTag("json-node:/items/0/title").performScrollTo().assertIsDisplayed()
        onNodeWithTag("http-row-1").performClick()
        onNodeWithTag("json-node:/items/0").assertDoesNotExist()
        onNodeWithTag("json-mode-text").performScrollTo().performClick()
        onNodeWithTag("http-body").performScrollTo().assertTextContains("Denim jacket", substring = true)
        onNodeWithTag("http-row-3").performClick()
        onNodeWithTag("json-mode-tree").assertIsSelected()
        onNodeWithTag("json-node:/items/0/title").performScrollTo().assertIsDisplayed()
    }

    @Test fun wideDetailsKeepContextAndCopyOnlyCapturedRedactedValues() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val mothlight = reviewFixture()
        val state = MothlightPanelState()
        val clipboard = MemoryClipboard()
        setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) {
            DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) }
        } }
        onNodeWithTag("http-row-3").performClick().assertIsSelected()
        onNodeWithTag("http-list").assertIsDisplayed()
        onNodeWithTag("http-details").assertIsDisplayed()
        onNodeWithTag("http-tab-Response").performClick()
        onNodeWithText("4410").assertIsDisplayed()
        onNodeWithTag("http-copy-body").performClick()
        runOnIdle { assertTrue(clipboard.contents.text.contains("\"id\":4410")) }
        onNodeWithTag("http-tab-Request").performClick()
        onNodeWithTag("http-copy-headers").performClick()
        runOnIdle {
            assertTrue(clipboard.contents.text.contains("Authorization: ***"))
            assertFalse(clipboard.contents.text.contains("fixture-private-token"))
        }
        onNodeWithTag("http-copy-curl").performClick()
        runOnIdle {
            assertTrue(clipboard.contents.text.startsWith("curl"))
            assertFalse(clipboard.contents.text.contains("fixture-private-token"))
        }
        onNodeWithTag("http-tab-Response").performClick()
        runOnIdle { state.selectTool("logs") }
        onNodeWithTag("http-details").assertDoesNotExist()
        runOnIdle { state.selectTool("http") }
        onNodeWithTag("http-row-3").assertIsSelected()
        onNodeWithTag("http-tab-Response").assertIsSelected()
        // New traffic must not replace the request currently being read.
        runOnIdle { mothlight.network.recordStart("GET", "https://example.com/new", emptyMap(), CapturedBody.NotCaptured, 9000) }
        onNodeWithTag("http-tab-Response").assertIsSelected()
        onNodeWithText("4410").assertIsDisplayed()
        val splitBefore = onNodeWithTag("http-list").fetchSemanticsNode().boundsInRoot.width
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput { swipe(center, center + Offset(-60f, 0f)) }
        runOnIdle { assertTrue(onNodeWithTag("http-list").fetchSemanticsNode().boundsInRoot.width < splitBefore) }
        onNodeWithTag("http-clear").performClick()
        onNodeWithTag("http-details").assertDoesNotExist()
        runOnIdle { assertTrue(mothlight.network.transactions.value.isEmpty()); assertEquals(4, mothlight.logs.items.value.size) }
    }

    @Test fun narrowDetailsUseWholeAreaAndRestoreListPositionAndFilters() = runDesktopComposeUiTest(width = 420, height = 860) {
        val mothlight = reviewFixture()
        val state = MothlightPanelState()
        setContent { DemoTheme(true) { MothlightPanel(mothlight, Modifier.fillMaxSize(), state = state) } }
        onNodeWithTag("http-filter-HttpErrors").performClick()
        onNodeWithTag("http-row-4").assertIsDisplayed()
        onNodeWithTag("http-row-5").assertDoesNotExist()
        onNodeWithTag("http-row-4").performClick()
        onNodeWithTag("http-list").assertDoesNotExist()
        onNodeWithTag("http-tab-Response").performClick()
        onNodeWithText("recommendations_unavailable", substring = true).assertIsDisplayed()
        onNodeWithTag("http-back").performClick()
        onNodeWithTag("http-filter-HttpErrors").assertIsSelected()
        onNodeWithTag("http-row-4").assertIsDisplayed()
        onNodeWithTag("http-search").performTextInput("does-not-exist")
        onNodeWithTag("http-row-4").assertDoesNotExist()
        runOnIdle { state.selectTool("sql") }
        onNodeWithTag("http-list").assertDoesNotExist()
        runOnIdle { state.selectTool("http") }
        onNodeWithTag("http-search").assertTextContains("does-not-exist")
        onNodeWithTag("http-search").performTextClearance()
        onNodeWithTag("http-filter-All").performClick()
        // Add enough records to require real scrolling, then return from details.
        runOnIdle { repeat(40) { mothlight.network.recordStart("GET", "https://example.com/item/$it", emptyMap(), CapturedBody.NotCaptured, 10000L + it) } }
        onNode(hasScrollToKeyAction()).performScrollToKey("10")
        val firstVisible = onNodeWithTag("http-row-10").fetchSemanticsNode().boundsInRoot.top
        onNodeWithTag("http-row-10").performClick()
        onNodeWithTag("http-tab-Response").assertIsSelected()
        onNodeWithTag("http-back").performClick()
        assertEquals(firstVisible, onNodeWithTag("http-row-10").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun optionsControlMemoryAndGlobalClear() = runDesktopComposeUiTest(width = 420, height = 860) {
        val mothlight = reviewFixture()
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize()) } }
        onNodeWithTag("panel-options").performClick()
        onNodeWithTag("panel-memory").performClick()
        runOnIdle { assertTrue(mothlight.memoryOverlay.enabled.value) }
        onNodeWithTag("panel-options").performClick()
        onNodeWithTag("panel-clear-all").performClick()
        runOnIdle { assertTrue(mothlight.logs.items.value.isEmpty()); assertTrue(mothlight.sql.snapshot.value.events.isEmpty()) }
    }
}

@Suppress("DEPRECATION")
private class MemoryClipboard : ClipboardManager {
    var contents = AnnotatedString("")
    override fun setText(annotatedString: AnnotatedString) { contents = annotatedString }
    override fun getText() = contents
}
