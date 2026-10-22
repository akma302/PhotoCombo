package com.combo.ui.common.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import com.combo.ui.common.designsystem.tokens.PcColors
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ui.common.designsystem.tokens.PcOpacity
import com.combo.ui.common.designsystem.tokens.PcShapes
import com.combo.ui.common.designsystem.tokens.PcSizes
import com.combo.ui.common.designsystem.tokens.PcSpacing
import com.combo.ui.common.designsystem.tokens.PcStrokes
import com.combo.ui.common.designsystem.tokens.PcTypography

private val LocalPcColors = staticCompositionLocalOf { PcColors.Dark }
private val LocalPcTypography = staticCompositionLocalOf { PcTypography() }
private val LocalPcSpacing = staticCompositionLocalOf { PcSpacing() }
private val LocalPcSizes = staticCompositionLocalOf { PcSizes() }
private val LocalPcShapes = staticCompositionLocalOf { PcShapes() }
private val LocalPcStrokes = staticCompositionLocalOf { PcStrokes() }
private val LocalPcOpacity = staticCompositionLocalOf { PcOpacity() }
private val LocalPcMotion = staticCompositionLocalOf { PcMotion() }
private val LocalPcDensity = staticCompositionLocalOf { PcDensity.Mouse }

val LocalPcContentColor = staticCompositionLocalOf { PcColors.Dark.text }
val LocalPcTextStyle = staticCompositionLocalOf<TextStyle> { PcTypography().body }

object PcTheme {
    val colors @Composable @ReadOnlyComposable get() = LocalPcColors.current
    val typography @Composable @ReadOnlyComposable get() = LocalPcTypography.current
    val spacing @Composable @ReadOnlyComposable get() = LocalPcSpacing.current
    val sizes @Composable @ReadOnlyComposable get() = LocalPcSizes.current
    val shapes @Composable @ReadOnlyComposable get() = LocalPcShapes.current
    val strokes @Composable @ReadOnlyComposable get() = LocalPcStrokes.current
    val opacity @Composable @ReadOnlyComposable get() = LocalPcOpacity.current
    val motion @Composable @ReadOnlyComposable get() = LocalPcMotion.current
    val density @Composable @ReadOnlyComposable get() = LocalPcDensity.current
    val contentColor @Composable @ReadOnlyComposable get() = LocalPcContentColor.current
    val minimumTarget @Composable @ReadOnlyComposable get() = maxOf(sizes.minimumTarget, density.minimumTarget)
}

/** Область собственных токенов UI; вложенная тема наследует настройки внешней. */
@Composable
fun PcTheme(
    colors: PcColors = PcTheme.colors,
    typography: PcTypography = PcTheme.typography,
    density: PcDensity = PcTheme.density,
    motion: PcMotion = PcTheme.motion,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPcColors provides colors,
        LocalPcTypography provides typography,
        LocalPcDensity provides density,
        LocalPcMotion provides motion,
        LocalPcContentColor provides colors.text,
        LocalPcTextStyle provides typography.body,
        content = content,
    )
}
