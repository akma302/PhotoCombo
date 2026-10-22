package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcActionGroupBoundsTest {
    @Test
    fun `bounded group clips offscreen commands and scrolls last command into the hit area`() = runComposeUiTest {
        var firstCalls = 0
        var lastCalls = 0
        var outsideCalls = 0
        setContent {
            PcTheme {
                Column {
                    PcActionGroup(Modifier.width(80.dp).height(34.dp).testTag("group")) {
                        PcButton("One", { firstCalls++ }, Modifier.width(70.dp))
                        PcButton("Two", { lastCalls++ }, Modifier.width(70.dp))
                    }
                    PcButton("Below", { outsideCalls++ }, Modifier.testTag("outside"))
                }
            }
        }
        onNodeWithTag("group").assertHeightIsEqualTo(34.dp)
        onNodeWithText("Two").assertIsNotDisplayed()
        onNodeWithTag("outside").performTouchInput { click(center) }
        runOnIdle {
            assertEquals(1, outsideCalls)
            assertEquals(0, firstCalls)
            assertEquals(0, lastCalls)
        }
        onNodeWithText("Two").performScrollTo().assertIsDisplayed().performTouchInput { click(center) }
        runOnIdle {
            assertEquals(1, lastCalls)
            assertEquals(0, firstCalls)
            assertEquals(1, outsideCalls)
        }
        val group = onNodeWithTag("group").fetchSemanticsNode().boundsInRoot
        val last = onNodeWithText("Two").fetchSemanticsNode().boundsInRoot
        assertTrue(last.top >= group.top && last.bottom <= group.bottom)
    }

    @Test
    fun `group in an unbounded scrolling gallery delegates scrolling to the gallery`() = runComposeUiTest {
        setContent {
            PcTheme {
                Column(Modifier.height(100.dp).verticalScroll(rememberScrollState())) {
                    PcActionGroup(Modifier.width(80.dp)) {
                        PcButton("First", {}, Modifier.width(70.dp))
                        PcButton("Middle", {}, Modifier.width(70.dp))
                        PcButton("Last", {}, Modifier.width(70.dp))
                    }
                }
            }
        }
        onNodeWithText("Last").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `normal RTL group keeps logical first action at the right`() = runComposeUiTest {
        setContent {
            PcTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PcActionGroup(Modifier.width(240.dp)) {
                        PcButton("First", {}, Modifier.width(80.dp))
                        PcButton("Second", {}, Modifier.width(80.dp))
                    }
                }
            }
        }
        val first = onNodeWithText("First").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Second").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left > second.left)
        assertEquals(first.top, second.top)
    }
}
