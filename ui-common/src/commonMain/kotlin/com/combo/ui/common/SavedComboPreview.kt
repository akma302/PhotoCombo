package com.combo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.render.CanvasTransform
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.data.backgroundFrames
import com.combo.data.toItemFrame

@Composable
fun SavedComboPreview(
    combo: ComboWithItems,
    photos: Map<String, PhotoEntity>,
    images: Map<String, ImageBitmap>,
    modifier: Modifier = Modifier,
) {
    val frames = remember(combo.items, photos, images) {
        combo.items.sortedBy { it.zIndex }.mapNotNull { item ->
            val image = images[item.photoId] ?: return@mapNotNull null
            val photo = photos[item.photoId] ?: return@mapNotNull null
            item.toItemFrame(image, photo)
        }
    }
    val canvasW = combo.combo.canvasWidth.toFloat()
    val canvasH = combo.combo.canvasHeight.toFloat()
    val background = remember(combo, photos) {
        comboBackgroundOf(
            combo.combo.canvasWidth,
            combo.combo.canvasHeight,
            combo.combo.background,
            combo.backgroundFrames(photos),
        )
    }
    Canvas(modifier) {
        val transform = CanvasTransform.fitInto(canvasW, canvasH, size.width, size.height)
        drawCombo(transform, canvasW, canvasH, background, frames)
    }
}
