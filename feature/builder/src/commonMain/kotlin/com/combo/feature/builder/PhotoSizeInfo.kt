package com.combo.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme

@Composable
internal fun PhotoSizeInfo(size: PhotoSize, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PcText("Photo size · ${size.percent}%", style = PcTheme.typography.caption, color = PcTheme.colors.textSecondary)
        PcText("${size.scaledWidth} × ${size.scaledHeight} px")
        PcText("Original ${size.originalWidth} × ${size.originalHeight} px", style = PcTheme.typography.caption, color = PcTheme.colors.textSecondary)
    }
}
