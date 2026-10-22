package com.combo.ui.common.designsystem.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class PcTokensTest {
    @Test
    fun `dark palette preserves every sRGB role including translucent overlays`() {
        val palette = PcColors.Dark
        val expected = listOf(
            0xFF202126, 0xFF292B31, 0xFF18191D, 0xFFF0F0F5, 0xFFA8AAB7,
            0xFF3B3D46, 0xFFB8A8FF, 0xFF24194B, 0xFF3C3454, 0xFF493C66,
            0xFFFF9CA5, 0x66000000, 0x55000000,
        ).map { it.toInt() }
        val actual = listOf(
            palette.app, palette.panel, palette.workspace, palette.text, palette.textSecondary,
            palette.border, palette.accent, palette.onAccent, palette.hover, palette.pressed,
            palette.danger, palette.scrim, palette.shadow,
        ).map(Color::toArgb)
        assertEquals(expected, actual)
    }

    @Test
    fun `typography uses provided family and exact role metrics`() {
        val typography = PcTypography(FontFamily.Monospace)
        val styles = listOf(
            typography.title, typography.body, typography.control,
            typography.controlStrong, typography.label, typography.caption,
        )
        assertEquals(listOf(14, 13, 13, 13, 12, 11).map { it.sp }, styles.map { it.fontSize })
        assertEquals(listOf(20, 19, 18, 18, 17, 16).map { it.sp }, styles.map { it.lineHeight })
        assertEquals(
            listOf(FontWeight.Medium, FontWeight.Normal, FontWeight.Normal,
                FontWeight.Medium, FontWeight.Normal, FontWeight.Normal),
            styles.map { it.fontWeight },
        )
        styles.forEach { assertEquals(FontFamily.Monospace, it.fontFamily) }
        assertEquals(FontFamily.SansSerif, PcTypography().body.fontFamily)
    }

    @Test
    fun `target density does not change visible control dimensions`() {
        assertEquals(34.dp, PcSizes().control)
        assertEquals(34.dp, PcSizes().iconButton)
        assertEquals(28.dp, PcSizes().compactControl)
        assertEquals(34.dp, PcDensity.Mouse.minimumTarget)
        assertEquals(44.dp, PcDensity.Touch.minimumTarget)
    }

    @Test
    fun `reduced motion removes transitions without removing tooltip delay`() {
        val motion = PcMotion(reducedMotion = true)
        assertEquals(0, motion.effectiveFeedbackMillis)
        assertEquals(0, motion.effectivePopupMillis)
        assertEquals(500, motion.tooltipDelayMillis)
        assertEquals(120, PcMotion().effectiveFeedbackMillis)
        assertEquals(160, PcMotion().effectivePopupMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative animation duration is rejected`() {
        PcMotion(feedbackMillis = -1)
    }
}
