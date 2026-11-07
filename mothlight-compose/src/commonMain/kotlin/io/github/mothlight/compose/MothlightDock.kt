package io.github.mothlight.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mothlight.compose.resources.*
import org.jetbrains.compose.resources.stringResource

/** End follows layout direction: right in LTR, left in RTL. */
enum class MothlightDockPlacement { Bottom, End }

/** Keep this state above the visibility gate to retain each placement's size when reopening. */
@Stable
class MothlightDockState(initialFraction: Float = 0.5f) {
    init { require(initialFraction.isFinite()) }
    var bottomFraction by mutableStateOf(initialFraction.coerceIn(0.25f, 0.75f))
        private set
    var endFraction by mutableStateOf(initialFraction.coerceIn(0.25f, 0.75f))
        private set

    fun fraction(placement: MothlightDockPlacement): Float = when (placement) {
        MothlightDockPlacement.Bottom -> bottomFraction
        MothlightDockPlacement.End -> endFraction
    }
    fun resize(placement: MothlightDockPlacement, fraction: Float) {
        if (!fraction.isFinite()) return
        when (placement) {
            MothlightDockPlacement.Bottom -> bottomFraction = fraction.coerceIn(0.25f, 0.75f)
            MothlightDockPlacement.End -> endFraction = fraction.coerceIn(0.25f, 0.75f)
        }
    }
}

/** Host and panel share the composition's theme. Drag the separator or use its progress action. */
@Composable
fun MothlightDock(
    visible: Boolean,
    modifier: Modifier = Modifier,
    placement: MothlightDockPlacement = MothlightDockPlacement.Bottom,
    state: MothlightDockState = remember { MothlightDockState() },
    resizeLabel: String = stringResource(Res.string.resize_panel),
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val hostContent = remember { movableContentOf { latestContent() } }
    BoxWithConstraints(modifier) {
        if (!visible) {
            Box(Modifier.fillMaxSize()) { hostContent() }
        } else {
            val bottom = placement == MothlightDockPlacement.Bottom
            val density = LocalDensity.current
            val availableHeight = (maxHeight - 12.dp).coerceAtLeast(0.dp)
            val availableWidth = (maxWidth - 12.dp).coerceAtLeast(0.dp)
            val extent = with(density) { (if (bottom) availableHeight else availableWidth).toPx() }
            val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            val fraction = state.fraction(placement)
            val drag = rememberDraggableState { delta ->
                if (extent > 0f) state.resize(placement,
                    state.fraction(placement) - delta * (if (!bottom && rtl) -1 else 1) / extent)
            }
            val separator: @Composable () -> Unit = {
                Box(
                    (if (bottom) Modifier.fillMaxWidth().height(12.dp) else Modifier.fillMaxHeight().width(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .draggable(drag, if (bottom) Orientation.Vertical else Orientation.Horizontal)
                        .semantics {
                            contentDescription = resizeLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0.25f..0.75f)
                            setProgress { state.resize(placement, it); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box((if (bottom) Modifier.width(40.dp).height(3.dp) else Modifier.height(40.dp).width(3.dp))
                        .background(MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small))
                }
            }
            if (bottom) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { hostContent() }
                    separator()
                    Box(Modifier.height(availableHeight * fraction).fillMaxWidth()) { panel() }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { hostContent() }
                    separator()
                    Box(Modifier.width(availableWidth * fraction).fillMaxHeight()) { panel() }
                }
            }
        }
    }
}
