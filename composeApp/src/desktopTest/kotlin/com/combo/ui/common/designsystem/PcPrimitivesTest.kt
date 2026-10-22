package com.combo.ui.common.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.components.PcActionGroup
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcColors
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcPrimitivesTest {
    @Test
    fun focusStripPreservesBoundsAndDisabledAlphaIncludesContent() = runSkikoComposeUiTest {
        lateinit var inputMode: InputModeManager
        setContent {
            inputMode = LocalInputModeManager.current
            PcTheme(motion = PcMotion(reducedMotion = true)) {
                PcSurface(Modifier.size(300.dp, 160.dp).testTag("surface"), color = PcTheme.colors.app) {
                    Column(Modifier.padding(16.dp)) {
                        PcButton("Save", {}, Modifier.testTag("enabled"), variant = PcButtonVariant.Primary)
                        PcButton("Save", {}, Modifier.testTag("disabled"), variant = PcButtonVariant.Primary, enabled = false)
                    }
                }
            }
        }
        val enabled = onNodeWithTag("enabled")
        val before = enabled.fetchSemanticsNode().boundsInRoot
        runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        enabled.requestFocus()
        val image = onNodeWithTag("surface").captureToImage().toPixelMap()
        assertEquals(before, enabled.fetchSemanticsNode().boundsInRoot)
        assertEquals(PcColors.Dark.app, image[(before.left - 3).toInt(), before.center.y.toInt()])
        assertEquals(PcColors.Dark.onAccent, image[before.center.x.toInt(), (before.bottom - 1).toInt()])
        val disabled = onNodeWithTag("disabled").captureToImage().toPixelMap()[8, 16]
        assertTrue(disabled.red < PcColors.Dark.accent.red)
        assertTrue(disabled.red > PcColors.Dark.app.red)
    }

    @Test
    fun actionGroupWrapsWholeCommandsAndPlacesRelativelyInRtl() = runSkikoComposeUiTest {
        setContent {
            PcTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PcActionGroup(Modifier.width(160.dp)) {
                        for (index in 1..3) Box(Modifier.size(70.dp, 34.dp).testTag("item$index")) { PcText("$index") }
                    }
                }
            }
        }
        val first = onNodeWithTag("item1").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag("item2").fetchSemanticsNode().boundsInRoot
        val third = onNodeWithTag("item3").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left > second.left)
        assertEquals(first.top, second.top)
        assertTrue(third.top >= first.bottom + 8f)
        assertEquals(first.left, third.left)
    }
}
