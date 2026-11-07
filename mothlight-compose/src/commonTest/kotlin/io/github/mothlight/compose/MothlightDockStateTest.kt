package io.github.mothlight.compose

import kotlin.test.*

class MothlightDockStateTest {
    @Test fun placementSizesAreIndependentAndBounded() {
        val state = MothlightDockState()
        state.resize(MothlightDockPlacement.Bottom, 0.7f)
        state.resize(MothlightDockPlacement.End, 0.3f)
        assertEquals(0.7f, state.fraction(MothlightDockPlacement.Bottom))
        assertEquals(0.3f, state.fraction(MothlightDockPlacement.End))
        state.resize(MothlightDockPlacement.End, 2f)
        assertEquals(0.75f, state.endFraction)
        state.resize(MothlightDockPlacement.Bottom, -1f)
        assertEquals(0.25f, state.bottomFraction)
        state.resize(MothlightDockPlacement.Bottom, Float.NaN)
        assertEquals(0.25f, state.bottomFraction)
    }
}
