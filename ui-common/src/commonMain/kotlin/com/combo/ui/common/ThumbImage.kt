package com.combo.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.ui.common.designsystem.theme.PcTheme

@Composable
fun ThumbImage(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(PcTheme.colors.workspace))
    }
}
