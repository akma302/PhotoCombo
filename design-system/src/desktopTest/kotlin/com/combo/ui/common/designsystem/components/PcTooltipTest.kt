package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcTooltipTest {
    @Test
    fun focusShowsTooltipWithoutInterceptingClickAndDisableDismissesIt() = runSkikoComposeUiTest(Size(400f, 200f)) {
        var clicks = 0
        val enabled = mutableStateOf(true)
        setContent {
            PcTheme(motion = PcMotion(tooltipDelayMillis = 20)) {
                Box(Modifier.padding(32.dp)) {
                    PcTooltip("Explanation", enabled = enabled.value) {
                        PcText("Action", Modifier.clickable { clicks++ }.padding(12.dp))
                    }
                }
            }
        }
        onNodeWithText("Explanation").assertDoesNotExist()
        onNodeWithText("Action").performSemanticsAction(SemanticsActions.RequestFocus)
        mainClock.advanceTimeBy(100)
        waitForIdle()
        onNodeWithText("Explanation").assertIsDisplayed()
        onNodeWithText("Action").performClick()
        assertEquals(1, clicks)
        runOnIdle { enabled.value = false }
        onNodeWithText("Explanation").assertDoesNotExist()
    }

    @Test
    fun popupPlacementStaysInsideWindowAndRespectsRtl() {
        val window = IntSize(300, 200)
        val popup = IntSize(100, 50)
        val anchor = IntRect(240, 170, 290, 195)
        for (direction in LayoutDirection.entries) {
            val position = PcAnchorPosition(8).calculatePosition(anchor, window, direction, popup)
            assertTrue(position.x in 0..200)
            assertTrue(position.y in 0..150)
            assertTrue(position.y + popup.height <= anchor.top)
        }
        assertEquals(190, PcAnchorPosition(8).calculatePosition(anchor, window, LayoutDirection.Rtl, popup).x)
    }
}
