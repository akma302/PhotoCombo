package com.combo

/**
 * Политика приложения: лимиты и пороги, общие для нескольких экранов. Значения — часть
 * поведения (спецификация §14), а не «настройки по вкусу».
 */
object Const {
    /** Непрозрачность заливки полей рамки (цвет фото) при вписывании фото целиком. */
    const val FILL_ALPHA = 0.5f

    /** Предел числа рамок в одном комбо. */
    const val MAX_ITEMS = 10

    /** Предел числа фото в одной рамке. */
    const val MAX_CONTENTS_PER_ITEM = 5

    /** Интервал автосмены фото по умолчанию. */
    const val DEFAULT_AUTO_INTERVAL_MS = 3000L

    /** Наименьшая сторона рамки — доля меньшей стороны холста. */
    const val MIN_BORDER_FRACTION = 0.08f

    /** Фон, когда вычислить нечего: ни рамок, ни фото, ни видимой площади. */
    const val FALLBACK_BACKGROUND = 0xFF202020.toInt()

    /**
     * Свайп считается сменой фото, если его скорость больше [FLING_MIN_VELOCITY] (px/с), а
     * вертикальная составляющая превосходит горизонтальную не меньше чем в [FLING_DOMINANCE]
     * раз. Сравнение — в `isVerticalFling`; один порог для редактора и плеера.
     */
    const val FLING_MIN_VELOCITY = 1600f
    const val FLING_DOMINANCE = 2f

    /** Границы зума вида редактора (относительно «вписать холст в экран»). */
    const val VIEW_ZOOM_MIN = 0.5f
    const val VIEW_ZOOM_MAX = 4f
}
