package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

object ItemGestures {
    /**
     * Перетаскивание рамки. Центр остаётся в границах холста: рамку, ушедшую целиком за
     * край, нечем было бы схватить и вернуть.
     */
    fun dragItem(item: ItemDraft, dScreen: Offset, vs: Float, canvasW: Float, canvasH: Float) {
        val moved = moveFrame(item.border(), dScreen.x / vs, dScreen.y / vs, canvasW, canvasH)
        item.centerX = moved.centerX
        item.centerY = moved.centerY
    }

    /** Pinch элемента: рамка + scale её фото (относительный зум сохранён). */
    fun pinchItem(item: ItemDraft, f: Float, minSize: Float) {
        if (item.width * f < minSize || item.height * f < minSize) return
        resizeItemProportionally(item, f)
    }

    internal fun resizeItemProportionally(item: ItemDraft, f: Float) {
        item.width *= f
        item.height *= f
        // pan живёт в координатах холста, поэтому при масштабировании рамки он должен
        // ехать вместе с ней — иначе кадр «плывёт» относительно рамки.
        item.contentScale *= f
        item.panX *= f
        item.panY *= f
    }

    fun panContent(c: ItemDraft, dScreen: Offset, vs: Float, b: Border, src: Size) {
        val clamped = TransformMath.clampPan(
            Offset(c.panX, c.panY) + dScreen / vs, b,
            TransformMath.contentSize(src.width.toInt(), src.height.toInt(), c.contentScale)
        )
        c.panX = clamped.x
        c.panY = clamped.y
    }

    fun zoomContent(c: ItemDraft, f: Float, src: Size) {
        val shortestSide = minOf(src.width, src.height)
        if (shortestSide <= 0f) return
        val target = c.contentScale * f
        if (!target.isFinite() || f < 0f) return
        // Keep the smallest rendered dimension at one canvas pixel.
        val min = 1f / shortestSide
        // A photo already below the minimum can still be zoomed in gradually.
        c.contentScale = if (f >= 1f) target else target.coerceAtLeast(minOf(c.contentScale, min))
    }

    /** Ресайз рамки за грань с фиксированным центром. */
    fun resizeBorder(item: ItemDraft, edge: Edge, dScreen: Offset, vs: Float, minSize: Float) {
        val border = item.border()
        val resized = FrameEdgeResize(border, edge, minSize, oneSided = false)
            .move(border, dScreen.x / vs, dScreen.y / vs)
        item.width = resized.width
        item.height = resized.height
    }
}

/** A resize keeps the same anchor for the whole gesture. */
internal class FrameEdgeResize(frame: Border, edge: Edge, minSize: Float, private val oneSided: Boolean) {
    val horizontal = edge == Edge.LEFT || edge == Edge.RIGHT
    val towardEnd = edge == Edge.RIGHT || edge == Edge.BOTTOM
    private val direction = if (towardEnd) 1f else -1f
    private val anchor = center(frame) - (if (oneSided) direction * halfSize(frame) else 0f)
    private val minExtent = if (oneSided) minSize else minSize / 2f

    fun coordinate(frame: Border): Float = center(frame) + direction * halfSize(frame)

    fun canReach(coordinate: Float): Boolean = extentAt(coordinate) >= minExtent

    fun move(frame: Border, dx: Float, dy: Float): Border =
        place(frame, coordinate(frame) + (if (horizontal) dx else dy))

    fun place(frame: Border, coordinate: Float): Border {
        val extent = extentAt(coordinate).coerceAtLeast(minExtent)
        val center = if (oneSided) anchor + direction * extent / 2f else anchor
        val size = if (oneSided) extent else extent * 2f
        return if (horizontal) frame.copy(centerX = center, width = size)
        else frame.copy(centerY = center, height = size)
    }

    private fun center(frame: Border) = if (horizontal) frame.centerX else frame.centerY
    private fun halfSize(frame: Border) = if (horizontal) frame.width / 2f else frame.height / 2f
    private fun extentAt(coordinate: Float) = (coordinate - anchor) * direction
}

internal fun moveFrame(frame: Border, dx: Float, dy: Float, canvasW: Float, canvasH: Float): Border =
    frame.copy(
        centerX = (frame.centerX + dx).coerceIn(0f, canvasW),
        centerY = (frame.centerY + dy).coerceIn(0f, canvasH),
    )
