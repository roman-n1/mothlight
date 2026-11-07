package io.github.mothlight.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mothlight.Mothlight
import io.github.mothlight.compose.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PanelThemeUiTest {
    @Test fun panelAndNestedToolsFollowHostColorsTypographyAndShapesOnThemeChange() = runDesktopComposeUiTest {
        val mothlight = Mothlight()
        var dark by mutableStateOf(false)
        val light = lightColorScheme(primary = Color.Magenta, surface = Color.Yellow)
        val night = darkColorScheme(primary = Color.Cyan, surface = Color.Blue)
        val typography = Typography(bodyMedium = TextStyle(fontSize = 19.sp))
        val shapes = Shapes(large = RoundedCornerShape(31.dp))
        val observedColors = mutableListOf<ColorScheme>()
        val observedTypography = mutableListOf<Typography>()
        val observedShapes = mutableListOf<Shapes>()
        val tools = mothlightTools(mothlight) {
            group("host-tools", { "Host tools" }) {
                tab("probe", { "Probe" }) {
                    val colors = MaterialTheme.colorScheme
                    val fonts = MaterialTheme.typography
                    val corners = MaterialTheme.shapes
                    SideEffect {
                        observedColors += colors
                        observedTypography += fonts
                        observedShapes += corners
                    }
                    Text("themed child")
                }
            }
        }
        val state = MothlightPanelState().apply { selectTool("host-tools") }
        setContent {
            MaterialTheme(colorScheme = if (dark) night else light, typography = typography, shapes = shapes) {
                MothlightPanel(mothlight, Modifier.fillMaxSize(), tools, state)
            }
        }
        onNodeWithText("themed child").assertIsDisplayed()
        runOnIdle {
            assertEquals(light, observedColors.last())
            assertEquals(typography, observedTypography.last())
            assertEquals(shapes, observedShapes.last())
            dark = true
        }
        runOnIdle {
            assertEquals(night, observedColors.last())
            assertEquals(typography, observedTypography.last())
            assertEquals(shapes, observedShapes.last())
        }
    }
}
