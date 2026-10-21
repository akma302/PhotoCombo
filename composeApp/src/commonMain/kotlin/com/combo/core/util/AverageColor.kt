package com.combo.core.util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Средний цвет изображения. Считается один раз при импорте фото и дальше хранится в
 * `PhotoEntity.avgColor`; при отрисовке не пересчитывается.
 */
object AverageColor {
    /**
     * Среднее арифметическое каналов sRGB по всем пикселям, взвешенное альфой, результат
     * непрозрачный. Для обычных фото (альфа везде 255) это просто среднее по пикселям.
     * Полностью прозрачное изображение даёт чёрный.
     *
     * Среднее считается в sRGB, а не в линейном пространстве: цвет идёт в фон и заливку
     * полей, а усреднение по рамкам делает отдельно стратегия фона (Oklab).
     */
    fun of(src: ImageBitmap): Int {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return OPAQUE_BLACK
        val pixels = IntArray(w * h)
        src.readPixels(pixels, 0, 0, w, h)

        var sumA = 0L
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            sumA += a
            sumR += ((p shr 16) and 0xFF) * a
            sumG += ((p shr 8) and 0xFF) * a
            sumB += (p and 0xFF) * a
        }
        if (sumA == 0L) return OPAQUE_BLACK
        val r = ((sumR + sumA / 2) / sumA).toInt()
        val g = ((sumG + sumA / 2) / sumA).toInt()
        val b = ((sumB + sumA / 2) / sumA).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private const val OPAQUE_BLACK = 0xFF000000.toInt()
}
