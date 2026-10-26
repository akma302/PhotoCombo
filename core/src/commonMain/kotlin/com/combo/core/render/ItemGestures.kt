package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max

object ItemGestures {
    /**
     * Перетаскивание рамки. Центр остаётся в границах холста: рамку, ушедшую целиком за
     * край, нечем было бы схватить и вернуть.
     */
    fun dragItem(item: ItemDraft, dScreen: Offset, vs: Float, canvasW: Float, canvasH: Float) {
        item.centerX = (item.centerX + dScreen.x / vs).coerceIn(0f, canvasW)
        item.centerY = (item.centerY + dScreen.y / vs).coerceIn(0f, canvasH)
    }

    /** Pinch элемента: рамка + scale её фото (относительный зум сохранён). */
    fun pinchItem(item: ItemDraft, f: Float, minSize: Float) {
        if (item.width * f < minSize || item.height * f < minSize) return
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

    fun zoomContent(c: ItemDraft, f: Float, b: Border, src: Size, vs: Float) {
        val min = TransformMath.fitScale(b, src) * 0.25f
        // Для мелких фото fitScale*0.25 может оказаться ВЫШЕ потолка (cap/vs) —
        // coerceIn(min, max) на таком диапазоне бросает IllegalArgumentException.
        val max = maxOf(min, TransformMath.maxContentScale(vs))
        val target = c.contentScale * f
        // Зум «на увеличение» никогда не уменьшает масштаб (иначе щипок за потолком
        // внезапно отдалял фото), а «на уменьшение» не опускает ниже min.
        c.contentScale = if (f >= 1f) maxOf(c.contentScale, minOf(target, max))
        else minOf(c.contentScale, maxOf(target, min))
    }

    /** Ресайз рамки за грани, якорь — центр (MVP-решение); только width ИЛИ height → любой аспект. */
    fun resizeBorder(item: ItemDraft, edge: Edge, dScreen: Offset, vs: Float, minSize: Float) {
        val d = dScreen / vs
        when (edge) {
            Edge.RIGHT -> item.width = max(minSize, item.width + 2 * d.x)
            Edge.LEFT -> item.width = max(minSize, item.width - 2 * d.x)
            Edge.BOTTOM -> item.height = max(minSize, item.height + 2 * d.y)
            Edge.TOP -> item.height = max(minSize, item.height - 2 * d.y)
        }
    }
}
