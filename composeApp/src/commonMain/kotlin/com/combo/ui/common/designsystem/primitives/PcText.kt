package com.combo.ui.common.designsystem.primitives

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.combo.ui.common.designsystem.theme.LocalPcTextStyle
import com.combo.ui.common.designsystem.theme.PcTheme

/** Текст наследует стиль и цвет поверхности, сохраняя штатную семантику BasicText. */
@Composable
fun PcText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PcTheme.contentColor,
    style: TextStyle = LocalPcTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(text, modifier, style.merge(TextStyle(color = color)), overflow = overflow, maxLines = maxLines)
}
