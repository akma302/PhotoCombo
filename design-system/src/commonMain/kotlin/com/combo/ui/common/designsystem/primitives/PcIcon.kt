package com.combo.ui.common.designsystem.primitives

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import com.combo.ui.common.designsystem.theme.PcTheme

/** Декоративный вектор не создаёт доступное имя; действие называет внешний контрол. */
@Composable
fun PcIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = PcTheme.contentColor,
    size: Dp = PcTheme.sizes.icon,
) {
    Image(rememberVectorPainter(imageVector), contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
