package com.combo.ui.common.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcTextField
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.components.PcSegmentedControl
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcColors
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

@OptIn(ExperimentalTestApi::class)
class PcFocusRenderingTest {
    private val colors = PcColors.Dark

    @Test
    fun lowerEdgeIsOpaqueWithFadingInnerLightAndBottomHaloAcrossScalesAndDirections() {
        for (scale in listOf(1f, 1.5f)) for (font in listOf(1f, 1.25f)) {
            for (direction in LayoutDirection.entries) runSkikoComposeUiTest(
                Size(420f, 220f), density = Density(scale, font),
            ) {
                lateinit var inputMode: InputModeManager
                var clicks = 0
                setContent {
                    inputMode = LocalInputModeManager.current
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        PcTheme(motion = PcMotion(reducedMotion = true)) {
                            Box(Modifier.testTag("scene").background(colors.workspace).padding(24.dp)) {
                                PcButton("Action", { clicks++ }, Modifier.width(150.dp).testTag("button"))
                            }
                        }
                    }
                }
                val node = onNodeWithTag("button")
                val before = node.fetchSemanticsNode().boundsInRoot
                runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
                node.requestFocus().assertIsFocused()
                val scene = onNodeWithTag("scene")
                val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
                val bounds = before.translate(-origin)
                val pixels = scene.captureToImage().toPixelMap()
                fun pixel(dx: Float, dy: Float) = pixels.sample(bounds.center.x + dx * scale, bounds.bottom + dy * scale)
                assertEquals(colors.accent, pixel(0f, -1f))
                assertEquals(colors.focusFill, pixel(-50f, -12f))
                assertTrue(pixel(-50f, -3f).red > pixel(-50f, -8f).red)
                assertTrue(pixel(-50f, -8f).red > pixel(-50f, -12f).red)
                assertTrue(pixel(0f, 0.5f).red > pixel(0f, 2.5f).red)
                assertTrue(pixel(0f, 2.5f).red > colors.workspace.red)
                assertEquals(colors.workspace, pixel(0f, 5f))
                assertEquals(colors.workspace, pixels.sample(bounds.left - 3f * scale, bounds.center.y))
                assertEquals(colors.workspace, pixels.sample(bounds.right + 3f * scale, bounds.center.y))
                assertEquals(colors.workspace, pixels.sample(bounds.center.x, bounds.top - 3f * scale))
                assertEquals(before, node.fetchSemanticsNode().boundsInRoot)
                node.performMouseInput { click(Offset(center.x, height - 1f)) }
                assertEquals(1, clicks)
                node.performMouseInput { click(Offset(center.x, height + 2f * scale)) }
                assertEquals(1, clicks)
            }
        }
    }

    @Test
    fun pointerButtonFocusHasNoStripeAndTabRevealsKeyboardFocus() = runSkikoComposeUiTest(Size(400f, 180f)) {
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Column(Modifier.padding(20.dp)) {
                    PcButton("First", {}, Modifier.width(150.dp).testTag("first"))
                    PcButton("Second", {}, Modifier.width(150.dp).testTag("second"))
                }
            }
        }
        val first = onNodeWithTag("first")
        first.performMouseInput { click(); moveTo(Offset(-10f, -10f)) }
        first.requestFocus().assertIsFocused()
        assertTrue(first.captureToImage().toPixelMap().let { it[it.width / 2, it.height - 2] } != colors.accent)
        first.performKeyInput { pressKey(Key.Tab) }
        val second = onNodeWithTag("second").assertIsFocused()
        assertEquals(colors.accent, second.captureToImage().toPixelMap().let { it[it.width / 2, it.height - 2] })
    }

    @Test
    fun enterAfterPointerFocusShowsKeyboardIndicatorAndActivatesOnce() = runSkikoComposeUiTest(Size(400f, 180f)) {
        var clicks = 0
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Box(Modifier.padding(20.dp)) { PcButton("Action", { clicks++ }, Modifier.testTag("button")) }
            }
        }
        val button = onNodeWithTag("button")
        button.performMouseInput { click(); moveTo(Offset(-10f, -10f)) }
        button.requestFocus().performKeyInput { pressKey(Key.Enter) }
        assertEquals(2, clicks)
        val image = button.captureToImage().toPixelMap()
        assertEquals(colors.accent, image[image.width / 2, image.height - 1])
    }

    @Test
    fun arrowAfterPointerSelectionShowsFocusOnNewlySelectedSegment() = runSkikoComposeUiTest(Size(400f, 180f)) {
        val selected = mutableStateOf("One")
        var changes = 0
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Box(Modifier.padding(20.dp)) {
                    PcSegmentedControl(listOf(PcOption("One", "One"), PcOption("Two", "Two")), selected.value,
                        { selected.value = it; changes++ })
                }
            }
        }
        onNodeWithText("One").performMouseInput { click(); moveTo(Offset(-10f, -10f)) }
        onNodeWithText("One").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        val target = onNodeWithText("Two").assertIsFocused().assertIsSelected()
        assertEquals("Two", selected.value)
        assertEquals(1, changes)
        val image = target.captureToImage().toPixelMap()
        assertEquals(colors.accent, image[image.width / 2, image.height - 1])
    }

    @Test
    fun mouseEditingKeepsErrorBorderStripeAndHalo() = runSkikoComposeUiTest(Size(400f, 220f)) {
        val text = mutableStateOf("")
        setContent {
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Box(Modifier.testTag("scene").background(colors.workspace).padding(20.dp)) {
                    PcTextField(text.value, { text.value = it }, "Name", Modifier.width(200.dp).testTag("field"), error = "Required")
                }
            }
        }
        val field = onNodeWithTag("field")
        field.performMouseInput { click() }
        field.assertIsFocused().performTextInput("Edited")
        assertEquals("Edited", text.value)
        val scene = onNodeWithTag("scene")
        val bounds = field.fetchSemanticsNode().boundsInRoot.translate(-scene.fetchSemanticsNode().boundsInRoot.topLeft)
        val pixels = scene.captureToImage().toPixelMap()
        assertEquals(colors.danger, pixels.sample(bounds.center.x, bounds.bottom - 1f))
        assertTrue(pixels.sample(bounds.left, bounds.center.y).red > colors.focusFill.red)
        assertTrue(pixels.sample(bounds.center.x, bounds.bottom + 1f).red > colors.workspace.red)
        assertTrue(pixels.sample(bounds.center.x, bounds.bottom + 1f).red > pixels.sample(bounds.center.x, bounds.bottom + 3f).red)
    }

    @Test
    fun primaryPreservesFillAndDarkStripeWhileBusyOrDisabledRemovesFocus() = runSkikoComposeUiTest(Size(400f, 180f)) {
        val enabled = mutableStateOf(true)
        val busy = mutableStateOf(false)
        lateinit var inputMode: InputModeManager
        setContent {
            inputMode = LocalInputModeManager.current
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                Box(Modifier.testTag("scene").background(colors.workspace).padding(20.dp)) {
                    PcButton("Save", {}, Modifier.width(180.dp).testTag("button"),
                        variant = PcButtonVariant.Primary, enabled = enabled.value, busy = busy.value)
                }
            }
        }
        val button = onNodeWithTag("button")
        runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        button.requestFocus()
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val bitmap = button.captureToImage().toPixelMap()
        assertEquals(colors.accent, bitmap[8, 12])
        assertEquals(colors.onAccent, bitmap[bitmap.width / 2, bitmap.height - 1])
        val scene = onNodeWithTag("scene")
        val local = bounds.translate(-scene.fetchSemanticsNode().boundsInRoot.topLeft)
        val halo = scene.captureToImage().toPixelMap().sample(local.center.x, local.bottom + 1f)
        assertTrue(halo.red > colors.workspace.red)
        runOnIdle { busy.value = true }
        button.assertIsNotEnabled()
        assertEquals(colors.workspace, scene.captureToImage().toPixelMap().sample(local.center.x, local.bottom + 1f))
        assertEquals(bounds, button.fetchSemanticsNode().boundsInRoot)
        runOnIdle { busy.value = false; enabled.value = false }
        assertEquals(colors.workspace, scene.captureToImage().toPixelMap().sample(local.center.x, local.bottom + 1f))
    }

    private fun PixelMap.sample(x: Float, y: Float): Color = this[floor(x).toInt(), floor(y).toInt()]
}
