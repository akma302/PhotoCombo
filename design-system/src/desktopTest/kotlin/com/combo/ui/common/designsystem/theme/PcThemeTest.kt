package com.combo.ui.common.designsystem.theme

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.tokens.PcColors
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ui.common.designsystem.tokens.PcTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcThemeTest {
    @Test
    fun `nested theme inherits settings and restores outer density after its scope`() = runComposeUiTest {
        val typography = PcTypography()
        val motion = PcMotion(reducedMotion = true)
        var outerTarget = 0.dp
        var innerTarget = 0.dp
        var restoredTarget = 0.dp
        setContent {
            PcTheme(typography = typography, motion = motion) {
                val target = PcTheme.minimumTarget
                SideEffect { outerTarget = target }
                PcTheme(density = PcDensity.Touch) {
                    val nestedTypography = PcTheme.typography
                    val nestedMotion = PcTheme.motion
                    val nestedTarget = PcTheme.minimumTarget
                    SideEffect {
                        assertSame(typography, nestedTypography)
                        assertSame(motion, nestedMotion)
                        innerTarget = nestedTarget
                    }
                }
                val restored = PcTheme.minimumTarget
                SideEffect { restoredTarget = restored }
            }
        }
        runOnIdle {
            assertEquals(34.dp, outerTarget)
            assertEquals(44.dp, innerTarget)
            assertEquals(34.dp, restoredTarget)
        }
    }

    @Test
    fun `palette change updates content color and leaves the outer scope intact`() = runComposeUiTest {
        val palette = mutableStateOf(PcColors.Dark)
        var currentText = Color.Unspecified
        var contentColor = Color.Unspecified
        var restoredText = Color.Unspecified
        setContent {
            PcTheme {
                PcTheme(colors = palette.value) {
                    val text = PcTheme.colors.text
                    val localContent = PcTheme.contentColor
                    val body = PcTheme.typography.body
                    val localStyle = LocalPcTextStyle.current
                    SideEffect {
                        currentText = text
                        contentColor = localContent
                        assertEquals(body, localStyle)
                    }
                }
                val restored = PcTheme.colors.text
                SideEffect { restoredText = restored }
            }
        }
        runOnIdle {
            assertEquals(PcColors.Dark.text, currentText)
            assertEquals(currentText, contentColor)
            palette.value = PcColors.Dark.copy(text = Color.Yellow)
        }
        runOnIdle {
            assertEquals(Color.Yellow, currentText)
            assertEquals(Color.Yellow, contentColor)
            assertEquals(PcColors.Dark.text, restoredText)
        }
    }
}
