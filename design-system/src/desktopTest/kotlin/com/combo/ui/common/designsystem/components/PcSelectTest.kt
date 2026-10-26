package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcSelectTest {
    private val options = listOf(PcOption(10, "Original"), PcOption(20, "Square"), PcOption(30, "Wide"))
    private fun option(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun reorderingOpenOptionsKeepsTheSameActiveValueForEnter() = runSkikoComposeUiTest(Size(500f, 350f)) {
        val choices = mutableStateOf(options)
        var picked = -1
        setContent {
            PcTheme { PcSelect(10, choices.value, { picked = it }, "Format", Modifier.testTag("select")) }
        }
        onNodeWithTag("select").performClick()
        onNode(option("Original")).performKeyInput { pressKey(Key.DirectionDown) }
        onNode(option("Square")).assertIsFocused()
        runOnIdle { choices.value = listOf(options[1], options[0], options[2]) }
        onNode(option("Square")).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(20, picked)
    }

    @Test
    fun removingActiveOptionFallsBackToSelectedValueAndCanContinueNavigating() = runSkikoComposeUiTest(Size(500f, 350f)) {
        val choices = mutableStateOf(options)
        var picked = -1
        setContent {
            PcTheme { PcSelect(10, choices.value, { picked = it }, "Format", Modifier.testTag("select")) }
        }
        onNodeWithTag("select").performClick()
        onNode(option("Original")).performKeyInput { pressKey(Key.MoveEnd) }
        onNode(option("Wide")).assertIsFocused()
        runOnIdle { choices.value = listOf(options[0], options[1]) }
        onNode(option("Original")).assertIsFocused()
        onNode(option("Original")).performKeyInput { pressKey(Key.DirectionDown) }
        onNode(option("Square")).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(20, picked)
    }

    @Test
    fun unconstrainedRowKeepsAnchorLabelOnOneVisibleLine() = runSkikoComposeUiTest(Size(500f, 350f)) {
        setContent {
            PcTheme {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    PcSelect(10, options, {}, "Format", Modifier.testTag("select"))
                }
            }
        }
        onNodeWithTag("select").assertHeightIsEqualTo(34.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Original", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        org.junit.Assert.assertTrue(layouts.single().size.width > 0)
        // При бесконечной ширине paragraph.width хранит Infinity; проверяем границу настоящей строки.
        org.junit.Assert.assertTrue(layouts.single().getLineRight(0) <= layouts.single().size.width)
    }

    @Test
    fun outsidePointerDismissesMenuAndRestoresAnchorFocus() = runSkikoComposeUiTest(Size(500f, 350f)) {
        setContent {
            PcTheme {
                Box(Modifier.fillMaxSize().testTag("host").padding(24.dp)) {
                    PcSelect(10, options, {}, "Format", Modifier.testTag("select"))
                }
            }
        }
        onNodeWithTag("select").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Format")).assertExists()
        onNodeWithTag("host").performMouseInput { click(bottomRight - androidx.compose.ui.geometry.Offset(4f, 4f)) }
        onNode(option("Square")).assertDoesNotExist()
        onNodeWithTag("select").assertIsFocused()
    }

    @Test
    fun pointerSelectsDirectValueAndChoosingCurrentValueDoesNotCallBack() = runSkikoComposeUiTest(Size(500f, 350f)) {
        val value = mutableStateOf(10)
        val changes = mutableListOf<Int>()
        setContent {
            PcTheme {
                Box(Modifier.padding(24.dp)) {
                    PcSelect(value.value, options, { value.value = it; changes += it }, "Canvas format", Modifier.testTag("select"))
                }
            }
        }
        val anchor = onNodeWithTag("select")
        anchor.assertContentDescriptionEquals("Canvas format")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .performClick()
        onNode(option("Wide")).performClick()
        assertEquals(listOf(30), changes)
        anchor.assertIsFocused().assertTextEquals("Wide").performClick()
        onNode(option("Wide")).assertIsSelected().performClick()
        assertEquals(listOf(30), changes)
        anchor.assertIsFocused()
    }

    @Test
    fun keyboardNavigationCommitsOnceAndEscapeReturnsFocusWithoutChangingValue() = runSkikoComposeUiTest(Size(500f, 350f)) {
        val value = mutableStateOf(10)
        val changes = mutableListOf<Int>()
        setContent {
            PcTheme {
                PcSelect(value.value, options, { value.value = it; changes += it }, "Format", Modifier.testTag("select"))
            }
        }
        val anchor = onNodeWithTag("select")
        anchor.requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
        onNode(option("Original")).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        onNode(option("Square")).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(20), changes)
        anchor.assertIsFocused().performKeyInput { pressKey(Key.Spacebar) }
        onNode(option("Square")).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Escape) }
        assertEquals(listOf(20), changes)
        anchor.assertIsFocused()
        onNode(option("Wide")).assertDoesNotExist()
    }

    @Test
    fun disabledDoesNotOpenOrActivateAndLosesPopupWhenDisabled() = runSkikoComposeUiTest(Size(500f, 350f)) {
        val enabled = mutableStateOf(false)
        var changes = 0
        setContent {
            PcTheme { PcSelect(10, options, { changes++ }, "Format", Modifier.testTag("select"), enabled.value) }
        }
        val anchor = onNodeWithTag("select")
        anchor.assertIsNotEnabled().performMouseInput { click() }
        onNode(option("Square")).assertDoesNotExist()
        runOnIdle { enabled.value = true }
        anchor.performClick()
        onNode(option("Square")).assertExists()
        runOnIdle { enabled.value = false }
        onNode(option("Square")).assertDoesNotExist()
        assertEquals(0, changes)
    }

    @Test
    fun longScrollableMenuKeepsActiveChoiceAcrossAnchorResize() = runSkikoComposeUiTest(Size(500f, 350f)) {
        val width = mutableStateOf(150.dp)
        val choices = (0..19).map { PcOption(it, "Option $it with a long descriptive label") }
        var selected = -1
        setContent {
            PcTheme {
                Box(Modifier.width(width.value)) {
                    PcSelect(0, choices, { selected = it }, "Format", Modifier.testTag("select"))
                }
            }
        }
        onNodeWithTag("select").performClick()
        onNode(option(choices.first().label)).performKeyInput { pressKey(Key.MoveEnd) }
        onNode(option(choices.last().label)).assertIsFocused().assertIsDisplayed()
        runOnIdle { width.value = 230.dp }
        onNode(option(choices.last().label)).assertIsFocused().performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(19, selected)
        onNodeWithTag("select").assertIsFocused()
    }
}
