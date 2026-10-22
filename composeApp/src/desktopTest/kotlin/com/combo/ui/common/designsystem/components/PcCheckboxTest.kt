package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcCheckboxTest {
    @Test
    fun `label click toggles once with a single checkbox node`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var calls = 0
        setContent {
            PcTheme {
                PcCheckbox(checked, { checked = it; calls++ }, "Always fit", Modifier.testTag("check"))
            }
        }
        onNodeWithTag("check").assertIsOff()
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(1)
        onAllNodes(hasClickAction()).assertCountEquals(1)
        onNodeWithText("Always fit").performClick()
        onNodeWithTag("check").assertIsOn()
        runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun `enter and space each toggle exactly once`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var calls = 0
        setContent {
            PcTheme {
                PcCheckbox(checked, { checked = it; calls++ }, "Always fit", Modifier.testTag("check"))
            }
        }
        onNodeWithTag("check").requestFocus().performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("check").assertIsOn()
        runOnIdle { assertEquals(1, calls) }
        onNodeWithTag("check").performKeyInput { pressKey(Key.Spacebar) }
        onNodeWithTag("check").assertIsOff()
        runOnIdle { assertEquals(2, calls) }
    }

    @Test
    fun `cancelled press does not toggle`() = runComposeUiTest {
        var calls = 0
        setContent { PcTheme { PcCheckbox(false, { calls++ }, "Fit", Modifier.testTag("check")) } }
        onNodeWithTag("check").performTouchInput {
            down(center)
            cancel()
        }
        runOnIdle { assertEquals(0, calls) }
        onNodeWithTag("check").assertIsOff()
    }

    @Test
    fun `disabled checkbox ignores input and tab skips it`() = runComposeUiTest {
        var calls = 0
        setContent {
            PcTheme {
                Column {
                    PcCheckbox(false, {}, "First", Modifier.testTag("first"))
                    PcCheckbox(false, { calls++ }, "Disabled", Modifier.testTag("disabled"), enabled = false)
                    PcCheckbox(false, {}, "Last", Modifier.testTag("last"))
                }
            }
        }
        onNodeWithTag("disabled").assertIsNotEnabled().performTouchInput { click() }
        onNodeWithTag("first").requestFocus().performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("last").assertIsFocused()
        runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun `touch density reserves target and large font grows the control`() = runComposeUiTest {
        setContent {
            PcTheme(density = PcDensity.Touch) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 3f)) {
                    PcCheckbox(false, {}, "Always fit", Modifier.testTag("check"))
                }
            }
        }
        onNodeWithTag("check").assertHeightIsAtLeast(54.dp).assertWidthIsAtLeast(44.dp)
    }
}
