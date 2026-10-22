package com.combo.ui.common.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PcColors(
    val app: Color,
    val panel: Color,
    val workspace: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val hover: Color,
    val pressed: Color,
    val focusFill: Color,
    val danger: Color,
    val scrim: Color,
    val shadow: Color,
) {
    companion object {
        val Dark = PcColors(
            app = Color(0xFF202126),
            panel = Color(0xFF292B31),
            workspace = Color(0xFF18191D),
            text = Color(0xFFF0F0F5),
            textSecondary = Color(0xFFA8AAB7),
            border = Color(0xFF3B3D46),
            accent = Color(0xFFDCE1E6),
            onAccent = Color(0xFF1C2528),
            hover = Color(0xFF35383E),
            pressed = Color(0xFF41454C),
            focusFill = Color(0xFF3A3C41),
            danger = Color(0xFFFF9CA5),
            scrim = Color(0x66000000),
            shadow = Color(0x55000000),
        )
    }
}
