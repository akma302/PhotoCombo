package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcColors
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcButtonTest {
    @Test
    fun busySpinnerDoesNotGrowShortLabelButtonAtEitherTargetDensity() = runSkikoComposeUiTest(Size(400f, 240f)) {
        val busy = mutableStateOf(false)
        val density = mutableStateOf(PcDensity.Mouse)
        setContent {
            PcTheme(density = density.value, motion = PcMotion(reducedMotion = true)) {
                PcButton("I", {}, Modifier.testTag("button"), busy = busy.value)
            }
        }
        val button = onNodeWithTag("button")
        for (target in PcDensity.entries) {
            runOnIdle { density.value = target; busy.value = false }
            val bounds = button.fetchSemanticsNode().boundsInRoot
            button.assertWidthIsEqualTo(target.minimumTarget)
            runOnIdle { busy.value = true }
            assertEquals(bounds, button.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun variantsRenderTheirNormalHoverAndPressedSurfaces() = runSkikoComposeUiTest(Size(400f, 300f)) {
        val colors = PcColors.Dark
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Column(Modifier.background(colors.app).padding(12.dp)) {
                    PcButtonVariant.entries.forEach { variant ->
                        PcButton("Action", {}, Modifier.testTag(variant.name), variant = variant)
                    }
                }
            }
        }
        fun surface(tag: String): Color = onNodeWithTag(tag).captureToImage().toPixelMap()[8, 16]
        assertEquals(colors.accent, surface("Primary"))
        assertEquals(colors.panel, surface("Secondary"))
        assertEquals(colors.app, surface("Ghost"))
        assertEquals(colors.app, surface("Danger"))
        val secondary = onNodeWithTag("Secondary")
        secondary.performMouseInput { moveTo(center) }
        assertEquals(colors.hover, surface("Secondary"))
        secondary.performMouseInput { press() }
        assertEquals(colors.pressed, surface("Secondary"))
        secondary.performMouseInput { release(); moveTo(Offset(-10f, -10f)) }
        assertEquals(colors.panel, surface("Secondary"))
    }

    @Test
    fun mouseAndKeyboardActivateExactlyOnceAndOutsideReleaseCancels() = runSkikoComposeUiTest(Size(400f, 240f)) {
        var clicks = 0
        setContent {
            PcTheme {
                Box(Modifier.padding(32.dp)) { PcButton("Save", { clicks++ }, Modifier.testTag("button")) }
            }
        }
        val button = onNodeWithTag("button")
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        button.performMouseInput { click() }
        assertEquals(1, clicks)
        button.requestFocus().performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(2, clicks)
        button.performKeyInput { pressKey(Key.Enter) }
        assertEquals(3, clicks)
        button.performMouseInput { moveTo(center); press(); moveTo(Offset(-20f, -20f)); release() }
        assertEquals(3, clicks)
    }

    @Test
    fun busyPreservesNameWidthAndIconSpacingWhileBlockingInput() = runSkikoComposeUiTest(Size(400f, 240f)) {
        var clicks = 0
        val busy = mutableStateOf(false)
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                PcButton("Save composition", { clicks++ }, Modifier.testTag("button"),
                    busy = busy.value, icon = PcIcons.Check)
            }
        }
        val button = onNodeWithTag("button")
        val initial = button.fetchSemanticsNode().boundsInRoot
        runOnIdle { busy.value = true }
        button.assertTextEquals("Save composition").assertIsNotEnabled()
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Busy"))
        assertEquals(initial, button.fetchSemanticsNode().boundsInRoot)
        button.performMouseInput { click() }
        assertEquals(0, clicks)
        runOnIdle { busy.value = false }
        button.assertIsEnabled().performMouseInput { click() }
        assertEquals(1, clicks)
    }

    @Test
    fun tabSkipsDisabledAndBusyButtons() = runSkikoComposeUiTest(Size(400f, 300f)) {
        var blockedClicks = 0
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Column {
                    PcButton("First", {}, Modifier.testTag("first"))
                    PcButton("Disabled", { blockedClicks++ }, Modifier.testTag("disabled"), enabled = false)
                    PcButton("Busy", { blockedClicks++ }, Modifier.testTag("busy"), busy = true)
                    PcButton("Last", {}, Modifier.testTag("last"))
                }
            }
        }
        onNodeWithTag("first").requestFocus().performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("last").assertIsFocused()
        onNodeWithTag("disabled").assertIsNotEnabled().performMouseInput { click() }
        onNodeWithTag("busy").assertIsNotEnabled().performMouseInput { click() }
        assertEquals(0, blockedClicks)
    }

    @Test
    fun touchMinimumAndWrappedTextGrowWithoutDroppingWords() = runSkikoComposeUiTest(Size(400f, 400f)) {
        val text = "Save this unusually long composition title"
        setContent {
            PcTheme(density = PcDensity.Touch) {
                Column {
                    PcButton("+", {}, Modifier.testTag("short"))
                    PcButton(text, {}, Modifier.width(140.dp).testTag("long"))
                }
            }
        }
        onNodeWithTag("short").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        onNodeWithTag("long").assertTextEquals(text).assertHeightIsAtLeast(44.dp)
        assertTrue(onNodeWithTag("long").fetchSemanticsNode().boundsInRoot.height >
            onNodeWithTag("short").fetchSemanticsNode().boundsInRoot.height)
    }
}
