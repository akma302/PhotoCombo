package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import com.combo.Const
import kotlin.math.abs
import kotlin.math.max
import com.combo.core.model.Direction

/**
 * Свайп достаточной скорости и преимущественно по вертикали = смена фото в рамке.
 *
 * Отдельная чистая функция, потому что это ровно тот порог, который обязан быть
 * одинаковым в билдере и в плеере, а проверять его удобно без Compose и без Robolectric.
 */
fun isVerticalFling(vx: Float, vy: Float): Boolean =
    abs(vy) > Const.FLING_MIN_VELOCITY && abs(vy) > abs(vx) * Const.FLING_DOMINANCE

/**
 * Направление смены фото по результату жеста либо `null`, если это не флинг.
 * `UP` — палец шёл вверх (`vy < 0`), `DOWN` — вниз.
 */
fun flingDirection(velocity: Velocity): Direction? {
    if (!isVerticalFling(velocity.x, velocity.y)) return null
    return if (velocity.y < 0) Direction.UP else Direction.DOWN
}

/**
 * Скорость жеста без исключений. `VelocityTracker.calculateVelocity()` бросает
 * исключение, если движений было слишком мало (например, палец отпустили, не сдвинув).
 * В этом случае возвращается `Velocity.Zero`, который [isVerticalFling] считает обычным
 * медленным жестом.
 */
fun VelocityTracker.safeVelocity(): Velocity = try {
    calculateVelocity()
} catch (e: Exception) {
    Velocity.Zero
}

object ItemGestures {
    /**
     * Перетаскивание рамки. Центр остаётся в границах холста: рамку, ушедшую целиком за
     * край, нечем было бы схватить и вернуть.
     */
    fun dragItem(item: ItemDraft, dScreen: Offset, vs: Float, canvasW: Float, canvasH: Float) {
        item.centerX = (item.centerX + dScreen.x / vs).coerceIn(0f, canvasW)
        item.centerY = (item.centerY + dScreen.y / vs).coerceIn(0f, canvasH)
    }

    /** Pinch элемента: рамка + scale ВСЕХ контентов (относительный зум сохранён). */
    fun pinchItem(item: ItemDraft, f: Float, minSize: Float) {
        if (item.width * f < minSize || item.height * f < minSize) return
        item.width *= f
        item.height *= f
        // pan живёт в координатах холста, поэтому при масштабировании рамки он должен
        // ехать вместе с ней — иначе кадр «плывёт» относительно рамки.
        item.contents.forEach {
            it.contentScale *= f
            it.panX *= f
            it.panY *= f
        }
    }

    fun panContent(c: ContentDraft, dScreen: Offset, vs: Float, b: Border, src: Size) {
        val clamped = TransformMath.clampPan(
            Offset(c.panX, c.panY) + dScreen / vs, b,
            TransformMath.contentSize(src.width.toInt(), src.height.toInt(), c.contentScale)
        )
        c.panX = clamped.x
        c.panY = clamped.y
    }

    fun zoomContent(c: ContentDraft, f: Float, b: Border, src: Size, vs: Float) {
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
