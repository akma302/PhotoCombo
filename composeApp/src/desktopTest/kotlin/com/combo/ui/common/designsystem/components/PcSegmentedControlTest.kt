package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcSegmentedControlTest {
    private val options = listOf(PcOption(1, "One"), PcOption(2, "Two"), PcOption(3, "Three"))

    @Test
    fun `radio group exposes one selected option and clicking changes once`() = runComposeUiTest {
        var value by mutableStateOf(1)
        var calls = 0
        setContent { PcTheme { PcSegmentedControl(options, value, { value = it; calls++ }) } }
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.SelectableGroup, Unit)).assertCountEquals(1)
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(3)
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
        onNodeWithText("Two").performClick().assertIsSelected()
        onNodeWithText("Two").performClick()
        runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun `arrows select and focus while enter and space activate once`() = runComposeUiTest {
        var value by mutableStateOf(1)
        var calls = 0
        setContent { PcTheme { PcSegmentedControl(options, value, { value = it; calls++ }) } }
        onNodeWithText("One").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Two").assertIsFocused().assertIsSelected()
        onNodeWithText("Three").requestFocus().performKeyInput { pressKey(Key.Enter) }
        onNodeWithText("Three").assertIsSelected()
        onNodeWithText("One").requestFocus().performKeyInput { pressKey(Key.Spacebar) }
        onNodeWithText("One").assertIsSelected()
        runOnIdle { assertEquals(3, calls) }
    }

    @Test
    fun `right arrow moves to previous logical option in RTL and wraps`() = runComposeUiTest {
        var value by mutableStateOf(1)
        setContent {
            PcTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PcSegmentedControl(options, value, { value = it })
                }
            }
        }
        onNodeWithText("One").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Three").assertIsSelected().assertIsFocused()
    }

    @Test
    fun `disabled segments are skipped in tab order`() = runComposeUiTest {
        var calls = 0
        setContent {
            PcTheme {
                Column {
                    PcCheckbox(false, {}, "Before", Modifier.testTag("before"))
                    PcSegmentedControl(options, 1, { calls++ }, enabled = false)
                    PcCheckbox(false, {}, "After", Modifier.testTag("after"))
                }
            }
        }
        onNodeWithText("Two").assertIsNotEnabled()
        onNodeWithTag("before").requestFocus().performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("after").assertIsFocused()
        runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun `long labels wrap at larger font scale and every segment shares height`() = runComposeUiTest {
        setContent {
            PcTheme(density = PcDensity.Touch) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.25f)) {
                    PcSegmentedControl(
                        listOf(PcOption(1, "A long option label"), PcOption(2, "Short")), 1, {},
                        Modifier.width(180.dp),
                    )
                }
            }
        }
        onNodeWithText("A long option label").assertHeightIsAtLeast(44.dp)
        val longHeight = onNodeWithText("A long option label").fetchSemanticsNode().boundsInRoot.height
        val shortHeight = onNodeWithText("Short").fetchSemanticsNode().boundsInRoot.height
        assertEquals(longHeight, shortHeight)
    }
}
