package com.combo.core.render

import androidx.compose.ui.geometry.Size
import kotlin.math.ceil
import kotlin.math.sqrt
import com.combo.core.model.FramingMode

object LayoutPresets {
    /**
     * fit: фото целиком + letterbox с заливкой; fill: cover + клип (кроп без искажений).
     *
     * Реализация fit — ровно та же, что у [effectiveFraming] в режиме `FIT`: иначе
     * кнопка «Fit» и режим AutoFit давали бы чуть разные масштабы (и «запечённое»
     * значение при выходе из AutoFit не совпадало бы с тем, что было на экране).
     */
    fun fit(c: ContentDraft, b: Border, src: Size) {
        val f = effectiveFraming(
            FramingMode.FIT, b, src.width.toInt(), src.height.toInt(),
            c.contentScale, c.panX, c.panY,
        )
        c.contentScale = f.scale; c.panX = f.panX; c.panY = f.panY
    }

    fun fill(c: ContentDraft, b: Border, src: Size) {
        c.contentScale = TransformMath.fillScale(b, src); c.panX = 0f; c.panY = 0f
    }

    /** Авто-раскладка: 2–4 фото — колонки, 5+ — грид. Дефолт контента — fill. */
    fun cellBorders(count: Int, canvasW: Float, canvasH: Float): List<Border> {
        require(count > 0)
        val cols = if (count <= 4) count else ceil(sqrt(count.toFloat())).toInt()
        val rows = ceil(count / cols.toFloat()).toInt()
        val cw = canvasW / cols
        val ch = canvasH / rows
        return (0 until count).map { i ->
            Border(cw * (i % cols) + cw / 2, ch * (i / cols) + ch / 2, cw, ch)
        }
    }
}
