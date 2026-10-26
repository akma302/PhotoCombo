package com.combo.ui.common.designsystem.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.combo.ui.common.designsystem.theme.PcTheme

/** Разделитель занимает место в layout; толщина масштабируется вместе с density. */
@Composable
fun PcDivider(modifier: Modifier = Modifier, vertical: Boolean = false, color: Color = PcTheme.colors.border) {
    val thickness = PcTheme.strokes.border
    Canvas(modifier.then(if (vertical) Modifier.fillMaxHeight().width(thickness) else Modifier.fillMaxWidth().height(thickness))) {
        drawRect(color)
    }
}
