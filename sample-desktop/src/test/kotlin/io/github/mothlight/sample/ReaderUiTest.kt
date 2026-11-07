package io.github.mothlight.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.mothlight.compose.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderUiTest {
    @Test fun dockDragsAlongBothAxesAndRetainsSizes() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val state = MothlightDockState()
        var visible by mutableStateOf(true)
        var placement by mutableStateOf(MothlightDockPlacement.Bottom)
        setContent {
            DemoTheme(false) {
                MothlightDock(visible, Modifier.fillMaxSize(), placement, state, "resize",
                    panel = { Text("panel") }, content = {
                        var count by remember { mutableStateOf(0) }
                        TextButton(onClick = { count++ }) { Text("host $count") }
                    })
            }
        }
        onNodeWithText("host 0").performClick()
        onNodeWithContentDescription("resize").performTouchInput { swipe(center, center + Offset(0f, -100f)) }
        runOnIdle { assertTrue(state.bottomFraction > 0.55f); placement = MothlightDockPlacement.End }
        onNodeWithContentDescription("resize").performTouchInput { swipe(center, center + Offset(-150f, 0f)) }
        var end = 0f
        runOnIdle { assertTrue(state.endFraction > 0.55f); end = state.endFraction; visible = false }
        onNodeWithText("panel").assertDoesNotExist()
        onNodeWithText("host 1").assertIsDisplayed()
        runOnIdle { visible = true }
        onNodeWithText("panel").assertIsDisplayed()
        runOnIdle { assertEquals(end, state.endFraction) }
        onNodeWithText("host 1").assertIsDisplayed()
    }

    @Test fun readerStartsWithoutPanelAndCanDockOnRight() = runDesktopComposeUiTest(width = 1240, height = 900) {
        val directory = createTempDirectory("leaf-ui").toFile()
        val demo = DemoSession(directory.resolve("reader.db"), fixtureEngine())
        try {
            setContent { DemoTheme(false) { ReaderApp(false, {}, demo) } }
            waitUntil(timeoutMillis = 15_000) { demo.ready.value && demo.articles.value.size == 6 && !demo.busy.value }
            onNodeWithTag("dock-close").assertDoesNotExist()
            capture("reader")
            onAllNodesWithTag("read-article")[0].performClick()
            onNodeWithTag("article-reader").assertIsDisplayed()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("article-full-text").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("article-full-text").assertTextContains("complete article text", substring = true)
            capture("article")
            onNodeWithTag("debug-toggle").performClick()
            onNodeWithTag("dock-close").assertIsDisplayed()
            capture("bottom-panel")
            onNodeWithTag("dock-placement").performClick()
            capture("right-panel")
            onNodeWithTag("article-back").performClick()
            onNodeWithTag("article-reader").assertDoesNotExist()
            onNodeWithTag("dock-close").performClick()
            onNodeWithTag("dock-placement").assertDoesNotExist()
        } finally { demo.close(); directory.deleteRecursively() }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.capture(name: String) {
    mainClock.advanceTimeBy(500)
    waitForIdle()
    val output = File("build/reports/demo-ui").apply { mkdirs() }
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "$name.png"))
}
