package com.combo.core.render

import androidx.compose.ui.geometry.Size
import com.combo.Const
import kotlin.math.abs
import com.combo.core.model.FramingMode

/** Формат холста: `width x height` в пикселях. */
data class CanvasFormat(val w: Int, val h: Int)

/**
 * Минимально допустимая сторона рамки: [Const.MIN_BORDER_FRACTION] от меньшей стороны
 * холста. Ниже этого значения ресайз и щипок рамку не уменьшают.
 */
fun minBorderSize(canvasW: Float, canvasH: Float): Float =
    Const.MIN_BORDER_FRACTION * minOf(canvasW, canvasH)

/**
 * Пресеты кнопки формата холста. Следующий пресет определяется по текущему размеру
 * холста (`state.canvasW/canvasH`), а не по индексу, который хранил бы тулбар: так кнопка
 * не может показать формат, отличный от открытого (например, после undo).
 */
object AspectPresets {

    /** Пять пресетов цикла, по порядку нажатия кнопки. */
    val all: List<CanvasFormat> = listOf(
        CanvasFormat(1600, 900),
        CanvasFormat(900, 1600),
        CanvasFormat(1200, 1200),
        CanvasFormat(1600, 1200),
        CanvasFormat(1200, 1600),
    )

    private val names: Map<CanvasFormat, String> = mapOf(
        CanvasFormat(1600, 900) to "16:9",
        CanvasFormat(900, 1600) to "9:16",
        CanvasFormat(1200, 1200) to "1:1",
        CanvasFormat(1600, 1200) to "4:3",
        CanvasFormat(1200, 1600) to "3:4",
    )

    /** Если текущий размер не совпал ни с одним пресетом — следующим идёт первый. */
    fun next(current: CanvasFormat): CanvasFormat {
        val idx = all.indexOf(current)
        return if (idx < 0) all.first() else all[(idx + 1) % all.size]
    }

    /**
     * Подпись для кнопки: `16:9`, `9:16`, `1:1`, `4:3`, `3:4`, а для произвольного
     * размера — `Custom WxH`. Именно по совпадению с пресетом, а не по сокращению
     * дроби: иначе 1000×700 показалось бы «10:7» вместо `Custom 1000x700`.
     */
    fun label(w: Int, h: Int): String {
        val f = CanvasFormat(w, h)
        return names[f] ?: "Custom ${w}x${h}"
    }
}

/**
 * Пересчёт рамок под новый формат холста. По умолчанию рамки следуют за холстом
 * ПРОПОРЦИОНАЛЬНО (решение владельца): центр и размер умножаются на `kx`/`ky`, но размер
 * не опускается ниже `MIN_BORDER_FRACTION` меньшей стороны нового холста.
 *
 * Содержимое рамки при этом не должно «поехать»:
 *  - рамка в [FramingMode.FIT] пересчитывает вписывание сама (см. [effectiveFraming]),
 *    поэтому её содержимое не трогаем вообще;
 *  - в FREE сохраняется «приближение относительно заполнения» `r = contentScale /
 *    fillScale(старая рамка)` — иначе фото, закрывавшее рамку, после смены формата
 *    внезапно показало бы пустые поля, а нарочно открытое — обрезалось бы;
 *  - у фото, закрывавшего рамку (`r >= 1`), pan дополнительно ограничивается так, чтобы
 *    фото ПРОДОЛЖАЛО закрывать рамку.
 *
 * A → B → A возвращает исходные координаты (в пределах точности float), если ни один размер
 * не упёрся в минимум. Координаты и размеры не округляются.
 */
fun relayout(
    items: List<ItemDraft>,
    oldW: Float,
    oldH: Float,
    newW: Float,
    newH: Float,
    srcSizeOf: (photoId: String) -> Size?,
) {
    if (oldW <= 0f || oldH <= 0f) return
    val kx = newW / oldW
    val ky = newH / oldH
    if (!kx.isFinite() || !ky.isFinite()) return
    val minSide = minBorderSize(newW, newH)

    for (item in items) {
        val oldBorder = item.border()
        val newCenterX = oldBorder.centerX * kx
        val newCenterY = oldBorder.centerY * ky
        val newBw = maxOf(oldBorder.width * kx, minSide)
        val newBh = maxOf(oldBorder.height * ky, minSide)

        if (item.framingMode != FramingMode.FIT) {
            val sx = if (oldBorder.width > 0f) newBw / oldBorder.width else 1f
            val sy = if (oldBorder.height > 0f) newBh / oldBorder.height else 1f
            for (c in item.contents) {
                val src = srcSizeOf(c.photoId) ?: continue
                if (src.width <= 0f || src.height <= 0f) continue
                // Приближение относительно заполнения считается по КАЖДОМУ фото: у них
                // разные пропорции, а значит и разный fillScale.
                val r = c.contentScale / TransformMath.fillScale(oldBorder, src)
                val newScale = r * TransformMath.fillScale(
                    Border(newCenterX, newCenterY, newBw, newBh), src,
                )
                c.contentScale = newScale
                c.panX = coverClamp(c.panX * sx, src.width * newScale, newBw)
                c.panY = coverClamp(c.panY * sy, src.height * newScale, newBh)
            }
        }

        item.centerX = newCenterX
        item.centerY = newCenterY
        item.width = newBw
        item.height = newBh
    }
}

/**
 * pan такой, чтобы фото закрывало рамку: центр контента не может уйти за
 * `±(размер фото − размер рамки) / 2`. Если фото по этой оси меньше рамки, граница
 * получается как раз «размер рамки минус размер фото» — столько же допустимо, сколько
 * было до пересчёта, и значение не зажимается лишний раз.
 */
private fun coverClamp(pan: Float, contentSize: Float, borderSize: Float): Float {
    val limit = abs(contentSize - borderSize) / 2f
    return pan.coerceIn(-limit, limit)
}
