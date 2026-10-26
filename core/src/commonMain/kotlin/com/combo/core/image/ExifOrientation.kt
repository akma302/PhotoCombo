package com.combo.core.image

/**
 * Ориентация пикселей фото относительно его «правильного» вида, приведённая к
 * значениям EXIF-тега Orientation (1–8). Собственный enum, а не тип Skia: домен
 * не должен зависеть от графической библиотеки конкретной платформы.
 */
enum class ExifOrientation(val exifValue: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8),
    ;

    /** Ориентации 5–8 меняют местами ширину и высоту кадра. */
    val swapsDimensions: Boolean
        get() = this >= TRANSPOSE
}

/** Неизвестные и нулевые значения EXIF трактуются как отсутствие ориентации. */
fun exifOrientationOf(value: Int): ExifOrientation =
    ExifOrientation.entries.firstOrNull { it.exifValue == value } ?: ExifOrientation.NORMAL
