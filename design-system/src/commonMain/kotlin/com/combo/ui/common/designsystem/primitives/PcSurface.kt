package com.combo.ui.common.designsystem.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.LocalPcContentColor
import com.combo.ui.common.designsystem.theme.PcTheme

/** Поверхность задаёт фон и наследуемый цвет без собственного интерактивного узла. */
@Composable
fun PcSurface(
    modifier: Modifier = Modifier,
    color: Color = PcTheme.colors.panel,
    contentColor: Color = PcTheme.colors.text,
    shape: Shape = RectangleShape,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPcContentColor provides contentColor) {
        Box(modifier.background(color, shape).then(
            if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier,
        )) { content() }
    }
}
