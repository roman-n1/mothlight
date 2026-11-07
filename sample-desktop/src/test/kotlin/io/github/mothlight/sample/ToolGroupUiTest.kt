package io.github.mothlight.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ToolGroupUiTest {
    @Test fun groupSelectionSurvivesLeavingAndOnlyVisibleChildCollects() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val mothlight = Mothlight()
        val active = mutableSetOf<String>()
        val groupState = MothlightPanelState()
        groupState.selectTool("second")
        val children = MothlightTools(listOf("first", "second").map { id ->
            MothlightTool(id, { id }) {
                DisposableEffect(id) { active += id; onDispose { active -= id } }
                Text("content $id")
            }
        })
        val panelState = MothlightPanelState().apply { selectTool("actron") }
        val tools = MothlightTools(listOf(
            toolGroup("actron", { "Actron" }, children, groupState),
            MothlightTool("host", { "Host" }) { Text("host content") },
        ))
        setContent { DemoTheme(false) { MothlightPanel(mothlight, Modifier.fillMaxSize(), tools, panelState) } }
        onNodeWithText("content second").assertIsDisplayed()
        runOnIdle { assertEquals(setOf("second"), active) }
        onNodeWithText("first").performClick()
        onNodeWithText("content first").assertIsDisplayed()
        onNodeWithText("content second").assertDoesNotExist()
        runOnIdle { assertEquals(setOf("first"), active) }
        onNodeWithText("Host").performClick()
        runOnIdle { assertTrue(active.isEmpty()) }
        onNodeWithText("Actron").performClick()
        onNodeWithText("content first").assertIsDisplayed()
        runOnIdle { assertEquals(setOf("first"), active) }
        runOnIdle { groupState.selectTool("removed-child") }
        onNodeWithText("content first").assertIsDisplayed()
    }
}
