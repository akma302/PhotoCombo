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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcTextFieldTest {
    @Test
    fun `text input preserves spaces and reports each edit once`() = runComposeUiTest {
        var value by mutableStateOf("")
        val changes = mutableListOf<String>()
        setContent {
            PcTheme {
                PcTextField(value, { value = it; changes += it }, "Name", Modifier.testTag("input"))
            }
        }
        onNodeWithTag("input").performTextInput("Hello")
        onNodeWithTag("input").performTextInput(" ")
        onNodeWithTag("input").performTextInput("world")
        onNodeWithTag("input").assertTextEquals("Hello world")
        runOnIdle { assertEquals(listOf("Hello", "Hello ", "Hello world"), changes) }
    }

    @Test
    fun `label and error belong to one editable semantics node`() = runComposeUiTest {
        setContent {
            PcTheme {
                PcTextField("abc", {}, "File name", Modifier.testTag("input"), error = "Name already exists")
            }
        }
        onAllNodes(hasSetTextAction()).assertCountEquals(1)
        onNodeWithTag("input")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("File name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Name already exists"))
    }

    @Test
    fun `readonly field is focusable and selectable without editing`() = runComposeUiTest {
        var calls = 0
        setContent {
            PcTheme { PcTextField("Original", { calls++ }, "Name", Modifier.testTag("input"), readOnly = true) }
        }
        onNodeWithTag("input").requestFocus().assertIsFocused()
        onNodeWithTag("input").performTextInputSelection(TextRange(0, 3))
        onNodeWithTag("input").performKeyInput { pressKey(Key.Spacebar) }
        onNodeWithTag("input").assertTextEquals("Original")
        runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun `disabled field is skipped during tab navigation`() = runComposeUiTest {
        var calls = 0
        setContent {
            PcTheme {
                Column {
                    PcCheckbox(false, {}, "Before", Modifier.testTag("before"))
                    PcTextField("Original", { calls++ }, "Name", Modifier.testTag("input"), enabled = false)
                    PcCheckbox(false, {}, "After", Modifier.testTag("after"))
                }
            }
        }
        onNodeWithTag("input").assertIsNotEnabled()
        onNodeWithTag("before").requestFocus().performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("after").assertIsFocused()
        runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun `input grows for large font scale and touch target`() = runComposeUiTest {
        setContent {
            PcTheme(density = PcDensity.Touch) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 3f)) {
                    PcTextField("Name", {}, "Label", Modifier.testTag("input"))
                }
            }
        }
        onNodeWithTag("input").assertHeightIsAtLeast(57.dp)
    }
}
