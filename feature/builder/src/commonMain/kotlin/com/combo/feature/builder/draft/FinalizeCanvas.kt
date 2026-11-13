package com.combo.feature.builder.draft

import androidx.compose.ui.geometry.Rect
import com.combo.core.render.Border
import com.combo.core.render.effectiveFraming
import com.combo.core.render.visiblePhotoBounds
import com.combo.data.PhotoEntity
import kotlin.math.ceil
import kotlin.math.floor

/** Crop the saved canvas around the photos while keeping the frames and their contents editable. */
internal fun ComboSnap.trimmedToPhotos(photos: Map<String, PhotoEntity>): ComboSnap {
    val canvas = Rect(0f, 0f, canvasW, canvasH)
    val visible = items.mapNotNull { item ->
        val frame = Border(item.centerX, item.centerY, item.width, item.height)
        val photo = photos[item.photoId]
        val bounds = if (photo == null || photo.width <= 0 || photo.height <= 0) {
            // Preserve the frame's area when there is insufficient metadata to trim it.
            frame.rect()
        } else {
            val framing = effectiveFraming(
                item.framingMode, frame, photo.width, photo.height, item.contentScale, item.panX, item.panY,
            )
            visiblePhotoBounds(frame, photo.width, photo.height, framing)
        }
        bounds.intersect(canvas).takeUnless { it.isEmpty }
    }
    if (visible.isEmpty()) return this

    val left = floor(visible.minOf { it.left })
    val top = floor(visible.minOf { it.top })
    val right = ceil(visible.maxOf { it.right })
    val bottom = ceil(visible.maxOf { it.bottom })
    return copy(
        canvasW = right - left,
        canvasH = bottom - top,
        items = items.map { it.copy(centerX = it.centerX - left, centerY = it.centerY - top) },
    )
}
