package com.combo.feature.builder.draft

import androidx.compose.ui.geometry.Rect
import com.combo.core.render.effectiveFraming
import com.combo.core.render.visiblePhotoBounds
import com.combo.data.PhotoEntity
import kotlin.math.ceil
import kotlin.math.floor

/** Whole-pixel bounds of the visible photos on the original canvas. */
internal fun ComboSnap.cropBounds(photos: Map<String, PhotoEntity>): Rect {
    val canvas = Rect(0f, 0f, canvasW, canvasH)
    val visible = items.mapNotNull { item ->
        val frame = item.border()
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
    if (visible.isEmpty()) return canvas

    return Rect(
        floor(visible.minOf { it.left }),
        floor(visible.minOf { it.top }),
        ceil(visible.maxOf { it.right }),
        ceil(visible.maxOf { it.bottom }),
    )
}

/** Translate the frames into the saved canvas while keeping their photo transforms editable. */
internal fun ComboSnap.cropTo(bounds: Rect): ComboSnap {
    if (bounds.width == canvasW && bounds.height == canvasH) return this
    return copy(
        canvasW = bounds.width,
        canvasH = bounds.height,
        items = items.map { it.copy(centerX = it.centerX - bounds.left, centerY = it.centerY - bounds.top) },
    )
}
