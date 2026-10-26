package com.combo.core.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import okio.Path

/** Формат файла при перезаписи нормализованного фото и миниатюр. */
enum class ImageFormat {
    JPEG,
    PNG,
}

/**
 * Кодирование/декодирование изображений платформы. Все методы терпимы к сбоям
 * на уровне отдельного файла: битый или нечитаемый файл даёт `null`/`false`,
 * а не исключение, — импорт пропускает такой файл и продолжает остальные.
 */
interface ImageCodec {
    /** Размер кадра до применения EXIF-ориентации; `null` — файл не читается. */
    fun readBounds(path: Path): IntSize?

    /** EXIF-ориентация файла; при любой ошибке — [ExifOrientation.NORMAL]. */
    fun readOrientation(path: Path): ExifOrientation

    /**
     * Декодирует фото с уменьшением примерно в [sample] раз по большей стороне
     * (`sample` — степень двойки, как `inSampleSize` на Android). EXIF-ориентация
     * НЕ применяется: нормализация выполняется один раз при импорте, к моменту
     * декодирования файл уже повёрнут. `null` — файл не декодируется.
     */
    fun decode(path: Path, sample: Int = 1): ImageBitmap?

    /** Перекодирует [image] в файл [path]; `false` — записать не удалось. */
    fun encode(path: Path, image: ImageBitmap, format: ImageFormat, quality: Int): Boolean
}
