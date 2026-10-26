package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcIconButtonTest {
    @Test
    fun accessibleNameBelongsToOneButtonAndMouseAndKeysActivateOnce() = runSkikoComposeUiTest(Size(300f, 200f)) {
        var clicks = 0
        setContent {
            PcTheme {
                Box(Modifier.padding(24.dp)) {
                    PcIconButton(PcIcons.Undo, "Undo edit", { clicks++ }, Modifier.testTag("button"))
                }
            }
        }
        val button = onNodeWithTag("button")
        onAllNodesWithContentDescription("Undo edit", useUnmergedTree = true).assertCountEquals(1)
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        button.assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(34.dp)
        button.performMouseInput { click() }
        assertEquals(1, clicks)
        button.requestFocus().performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(2, clicks)
        button.performKeyInput { pressKey(Key.Enter) }
        assertEquals(3, clicks)
    }

    @Test
    fun focusAndHoverShowNameTooltipWithoutTakingClicksAndDisableDismissesIt() = runSkikoComposeUiTest(Size(300f, 200f)) {
        val enabled = mutableStateOf(true)
        var clicks = 0
        setContent {
            PcTheme(motion = PcMotion(tooltipDelayMillis = 20)) {
                Box(Modifier.padding(32.dp)) {
                    PcIconButton(PcIcons.Delete, "Delete photo", { clicks++ },
                        Modifier.testTag("button"), enabled.value)
                }
            }
        }
        val button = onNodeWithTag("button")
        button.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(100)
        waitForIdle()
        onNodeWithText("Delete photo").assertIsDisplayed()
        button.performMouseInput { click() }
        assertEquals(1, clicks)
        button.requestFocus()
        mainClock.advanceTimeBy(100)
        waitForIdle()
        button.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(2, clicks)
        runOnIdle { enabled.value = false }
        onNodeWithText("Delete photo").assertDoesNotExist()
        button.assertIsNotEnabled().performMouseInput { click() }
        assertEquals(2, clicks)
    }

    @Test
    fun touchDensityExpandsEntireClickableTarget() = runSkikoComposeUiTest(Size(300f, 200f)) {
        var clicks = 0
        setContent {
            PcTheme(density = PcDensity.Touch) {
                PcIconButton(PcIcons.Add, "Add", { clicks++ }, Modifier.testTag("button"))
            }
        }
        val button = onNodeWithTag("button")
        button.assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        button.performMouseInput { click(topLeft + androidx.compose.ui.geometry.Offset(2f, 2f)) }
        assertEquals(1, clicks)
    }
}
