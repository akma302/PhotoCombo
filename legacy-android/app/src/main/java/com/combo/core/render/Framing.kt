package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.model.FramingMode

/**
 * Как фото вписывается в рамку — см. `com.combo.core.model.FramingMode`.
 *
 * Итог: одна шкала и смещение центра контента относительно центра рамки.
 */
data class Framing(val scale: Float, val panX: Float, val panY: Float)

/**
 * ЕДИНСТВЕННОЕ место, где решается, как фото лежит в рамке. Редактор, плеер и превью
 * списка идут сюда через [contentArgsOf]; иначе AutoFit в редакторе и в плеере
 * разошёлся бы, а совпадение картинки везде (WYSIWYG) — главный принцип проекта.
 *
 * Размеры фото неизвестны (или нулевые) → возвращаем сохранённые значения: визуально
 * ничего не изменится, зато пустой кадр не превратится в scale = NaN.
 */
fun effectiveFraming(
    mode: FramingMode,
    border: Border,
    srcW: Int,
    srcH: Int,
    storedScale: Float,
    storedPanX: Float,
    storedPanY: Float,
): Framing = when (mode) {
    FramingMode.FREE -> Framing(storedScale, storedPanX, storedPanY)
    FramingMode.FIT -> if (srcW <= 0 || srcH <= 0) {
        Framing(storedScale, storedPanX, storedPanY)
    } else {
        Framing(fitScaleOf(border, srcW, srcH), 0f, 0f)
    }
}

/**
 * Общий помощник для всех путей отрисовки: собирает [ContentDrawArgs], пропуская
 * сохранённые значения через [effectiveFraming]. Режим рамки учитывается только здесь,
 * поэтому собирать [ContentDrawArgs] в обход этой функции нельзя.
 */
fun contentArgsOf(
    mode: FramingMode,
    image: ImageBitmap,
    border: Border,
    srcW: Int,
    srcH: Int,
    storedScale: Float,
    storedPanX: Float,
    storedPanY: Float,
): ContentDrawArgs {
    val f = effectiveFraming(mode, border, srcW, srcH, storedScale, storedPanX, storedPanY)
    return ContentDrawArgs(
        image = image,
        srcW = srcW,
        srcH = srcH,
        scale = f.scale,
        pan = Offset(f.panX, f.panY),
    )
}

/**
 * Единственная формула «вписать целиком»: та же, что у [LayoutPresets.fit] и
 * [TransformMath.fitScale], только для целочисленных размеров фото (как в базе).
 */
private fun fitScaleOf(border: Border, srcW: Int, srcH: Int): Float =
    TransformMath.fitScale(border, Size(srcW.toFloat(), srcH.toFloat()))