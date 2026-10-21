package com.combo.core.background

import com.combo.Const
import com.combo.core.color.Oklab
import kotlin.math.max
import kotlin.math.min

/**
 * Фон по умолчанию: среднее цветов фото, взвешенное по ВИДИМОЙ площади каждой рамки.
 * Усредняется в OKLab (Björn Ottosson), а не в sRGB: у гамма-кодированного sRGB среднее
 * смещает яркость и насыщенность, а OKLab ближе к тому, как цвета воспринимаются глазом.
 *
 * Площади перекрывающихся рамок НЕ вычитаются (берётся простая сумма) — сознательно:
 * иначе вес перекрытой рамки менялся бы при обычном перетаскивании и фон дёргался бы,
 * а точность тут не нужна: фон виден только по краям.
 */
object AverageAreaOklabStrategy : BackgroundStrategy {

    const val ID = "avg.area.oklab.v1"

    override val id: String get() = ID

    override fun resolve(input: BackgroundInput): ResolvedBackground {
        var weightSum = 0.0
        var lSum = 0.0
        var aSum = 0.0
        var bSum = 0.0

        for (frame in input.frames) {
            if (frame.photoColors.isEmpty()) continue
            val area = visibleArea(frame, input.canvasW, input.canvasH)
            if (area <= 0f) continue
            // Все фото рамки делят её вес поровну: иначе фон мигал бы при смене фото.
            val w = area / frame.photoColors.size
            for (argb in frame.photoColors) {
                val (l, a, b) = Oklab.fromArgb(argb)
                weightSum += w
                lSum += w * l
                aSum += w * a
                bSum += w * b
            }
        }

        if (weightSum <= 0.0) return ResolvedBackground.Solid(Const.FALLBACK_BACKGROUND)
        return ResolvedBackground.Solid(Oklab.toArgb(lSum / weightSum, aSum / weightSum, bSum / weightSum))
    }

    /** Пересечение прямоугольника рамки с холстом; пустое пересечение даёт вес 0. */
    private fun visibleArea(frame: FrameInput, canvasW: Float, canvasH: Float): Float {
        val w = min(frame.right, canvasW) - max(frame.left, 0f)
        val h = min(frame.bottom, canvasH) - max(frame.top, 0f)
        if (w <= 0f || h <= 0f) return 0f
        return w * h
    }

    /** Только для тестов: линейный доступ к компонентам OKLab. */
    internal fun oklab(argb: Int): Triple<Double, Double, Double> = Oklab.fromArgb(argb)

    /** Только для тестов: обратное преобразование с округлением до канала. */
    internal fun fromOklab(l: Double, a: Double, b: Double): Int = Oklab.toArgb(l, a, b)
}