package com.combo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ui.common.designsystem.tokens.PcTypography
import java.awt.GraphicsEnvironment

/** Выбор системного шрифта остаётся на стороне desktop; файлы шрифтов не копируются. */
@OptIn(ExperimentalTextApi::class)
object PcDesktopFonts {
    val resolvedFamily: String by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
            .firstOrNull { it.equals("Segoe UI", ignoreCase = true) } ?: "SansSerif"
    }
    val typography: PcTypography by lazy {
        PcTypography(if (resolvedFamily == "SansSerif") FontFamily.SansSerif else FontFamily(
            SystemFont(resolvedFamily, FontWeight.Normal), SystemFont(resolvedFamily, FontWeight.Medium),
        ))
    }
}

@Composable
fun PcDesktopTheme(density: PcDensity = PcDensity.Mouse, motion: PcMotion = PcMotion(), content: @Composable () -> Unit) {
    val typography = remember { PcDesktopFonts.typography }
    PcTheme(typography = typography, density = density, motion = motion, content = content)
}
