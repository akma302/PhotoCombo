package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcColors
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

@OptIn(ExperimentalTestApi::class)
class PcSegmentedFocusOutlineTest {
    @Test
    fun `first selected segment paints bottom strip without trailing external ring`() = verifyOutline("One")

    @Test
    fun `second selected segment paints bottom strip without leading external ring`() = verifyOutline("Two")

    @Test
    fun `equal widths fit the longest segment without wrapping when space is available`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PcTheme {
                    PcSegmentedControl(
                        listOf(PcOption(1, "Whole canvas", PcIcons.Frame), PcOption(2, "Fit", PcIcons.Frame)),
                        1, {}, Modifier.testTag("segments"),
                    )
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText("Whole canvas").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        onNodeWithTag("segments").assertHeightIsEqualTo(34.dp)
    }

    private fun verifyOutline(label: String) = runComposeUiTest {
        lateinit var inputMode: InputModeManager
        setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PcTheme {
                    Box(Modifier.testTag("scene").background(PcTheme.colors.workspace).padding(12.dp)) {
                        PcSegmentedControl(
                            listOf(PcOption("One", "One"), PcOption("Two", "Two")), label, {},
                            Modifier.testTag("segments"),
                        )
                    }
                }
            }
        }
        val before = onNodeWithTag("segments").fetchSemanticsNode().boundsInRoot
        val scene = onNodeWithTag("scene")
        val baseline = scene.captureToImage().toPixelMap()
        val segment = onNodeWithText(label)
        runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        segment.requestFocus().assertIsFocused().assertIsSelected()
        val bounds = segment.fetchSemanticsNode().boundsInRoot
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val pixels = scene.captureToImage().toPixelMap()
        val x = (if (label == "One") bounds.right + 3f else bounds.left - 3f) - sceneBounds.left
        val y = bounds.center.y - sceneBounds.top
        assertEquals(baseline[x.roundToInt(), y.roundToInt()].toArgb(), pixels[x.roundToInt(), y.roundToInt()].toArgb())
        assertEquals(PcColors.Dark.accent.toArgb(), pixels[
            (bounds.center.x - sceneBounds.left).roundToInt(),
            (bounds.bottom - sceneBounds.top - 1f).roundToInt(),
        ].toArgb())
        assertEquals(before, onNodeWithTag("segments").fetchSemanticsNode().boundsInRoot)
    }
}
