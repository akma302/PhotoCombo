package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcSliderTest {
    @Test fun pointerUpdatesImmediatelyAndCommitsOnceOnRelease() = runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(400f, 200f)) {
        var value by mutableStateOf(100f)
        var finished = 0
        setContent { PcTheme { PcSlider(value, 50f..200f, { value = it }, { finished++ }, "Size", Modifier.width(300.dp).testTag("slider")) } }
        val slider = onNodeWithTag("slider")
        slider.performMouseInput { moveTo(center); press(); moveTo(Offset(width * .8f, height / 2f)) }
        runOnIdle { assertTrue(value > 150f); assertEquals(0, finished) }
        slider.performMouseInput { release() }
        runOnIdle { assertEquals(1, finished) }
        slider.performMouseInput { click(Offset(12f, height / 2f)) }
        runOnIdle { assertEquals(50f, value, .01f); assertEquals(2, finished) }
    }

    @Test fun keyboardSemanticsDisabledAndRtlUseTheSameValueContract() = runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(400f, 200f)) {
        var value by mutableStateOf(100f)
        var enabled by mutableStateOf(true)
        var rtl by mutableStateOf(false)
        var finished = 0
        setContent { PcTheme {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PcSlider(value, 50f..200f, { value = it }, { finished++ }, "Size",
                    Modifier.width(300.dp).testTag("slider"), enabled)
            }
        } }
        val slider = onNodeWithTag("slider")
        slider.requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        runOnIdle { assertEquals(101f, value); assertEquals(1, finished) }
        slider.performKeyInput { pressKey(Key.MoveEnd); pressKey(Key.DirectionUp) }
        runOnIdle { assertEquals(200f, value); assertEquals(2, finished) }
        slider.performKeyInput { pressKey(Key.MoveHome) }
        runOnIdle { assertEquals(50f, value) }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(125f) }
        runOnIdle { assertEquals(125f, value); rtl = true }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        runOnIdle { assertEquals(124f, value) }
        slider.performMouseInput { click(Offset(12f, height / 2f)) }
        runOnIdle { assertEquals(200f, value, .01f); enabled = false }
        slider.assertIsNotEnabled()
        val count = finished
        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(70f)) }
        slider.performMouseInput { click(center) }
        runOnIdle { assertEquals(200f, value, .01f); assertEquals(count, finished) }
    }
    @Test fun disablingDuringGestureRestoresPreviewWithoutCommitting() = runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(400f, 200f)) {
        var value by mutableStateOf(100f)
        var enabled by mutableStateOf(true)
        var finished = 0
        setContent { PcTheme { PcSlider(value, 50f..200f, { value = it }, { finished++ }, "Size", Modifier.width(300.dp).testTag("slider"), enabled) } }
        val slider = onNodeWithTag("slider")
        slider.performMouseInput { moveTo(center); press(); moveTo(Offset(width * .8f, center.y)) }
        runOnIdle { assertTrue(value > 150f); enabled = false }
        waitForIdle()
        runOnIdle { assertEquals(100f, value); assertEquals(0, finished) }
        slider.performMouseInput { release() }
        runOnIdle { enabled = true }
        slider.performMouseInput { rightClick() }
        runOnIdle { assertEquals(100f, value); assertEquals(0, finished) }
    }
}
