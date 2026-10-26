package com.combo.core.color

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Конвертации sRGB ↔ OKLab (Björn Ottosson). Вынесены из фоновой стратегии, чтобы
 * математику можно было использовать и проверять отдельно от неё: round-trip и сверка с
 * эталонными значениями не требуют резолвера фона.
 *
 * ВНИМАНИЕ: коэффициенты матриц взяты из спецификации OKLab. Их нельзя подгонять под
 * конкретные цвета или под «красивые» значения в тестах: любая правка меняет все
 * усреднения цветов в приложении. Порядок вычислений тоже сохраняется.
 */
object Oklab {

    fun fromArgb(argb: Int): Triple<Double, Double, Double> {
        val r = linearize(argb shr 16 and 0xFF)
        val g = linearize(argb shr 8 and 0xFF)
        val b = linearize(argb and 0xFF)

        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val l_ = cbrt(l)
        val m_ = cbrt(m)
        val s_ = cbrt(s)

        return Triple(
            0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
            1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
            0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
        )
    }

    fun toArgb(l: Double, a: Double, b: Double): Int {
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b

        val ll = l_ * l_ * l_
        val mm = m_ * m_ * m_
        val ss = s_ * s_ * s_

        val r = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss
        val g = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss
        val bb = -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss

        // Результат всегда непрозрачный: альфа входного цвета игнорируется.
        return (0xFF shl 24) or
            (encode(r) shl 16) or
            (encode(g) shl 8) or
            encode(bb)
    }

    private fun linearize(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun encode(linear: Double): Int {
        val c = linear.coerceIn(0.0, 1.0)
        val srgb = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (srgb.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    }

    private fun cbrt(v: Double): Double = if (v < 0) -kotlin.math.cbrt(-v) else kotlin.math.cbrt(v)
}