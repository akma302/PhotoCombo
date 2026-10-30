package com.combo.feature.builder

import com.combo.core.render.effectiveFraming
import kotlin.math.roundToInt

/** Immutable dimensions of the selected photo in canvas pixels. */
data class PhotoSize(
    val frameId: String,
    val photoId: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val percent: Int,
)

internal fun BuilderUiState.selectedPhotoSize(): PhotoSize? {
    val item = items.firstOrNull { it.id == selectedItemId } ?: return null
    val photo = photos[item.photoId] ?: return null
    if (photo.width <= 0 || photo.height <= 0) return null
    val scale = effectiveFraming(item.framingMode, item.border(), photo.width, photo.height,
        item.contentScale, item.panX, item.panY).scale
    if (!scale.isFinite() || scale <= 0f) return null
    return PhotoSize(item.id, photo.id, photo.width, photo.height,
        (photo.width * scale).roundToInt(), (photo.height * scale).roundToInt(), (scale * 100).roundToInt())
}
