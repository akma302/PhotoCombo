package com.combo.core.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * Приводит пиксели [image] к «правильному» виду по EXIF-ориентации [orientation]
 * и возвращает новый ImageBitmap. Для [ExifOrientation.NORMAL] возвращает исходный
 * объект без копии: подавляющее большинство фото не требует преобразования.
 *
 * Интерполяция отключена ([FilterQuality.None]): ориентация — это перестановка
 * пикселей, а не масштабирование, сглаживание только размывало бы кадр.
 * Все повороты и отражения выполняются относительно начала координат — именно
 * такие последовательности задают матрицы EXIF (см. таблицу ориентаций).
 */
fun applyExifOrientation(image: ImageBitmap, orientation: ExifOrientation): ImageBitmap {
    if (orientation == ExifOrientation.NORMAL) return image

    val w = image.width.toFloat()
    val h = image.height.toFloat()
    val outW = if (orientation.swapsDimensions) image.height else image.width
    val outH = if (orientation.swapsDimensions) image.width else image.height

    val result = ImageBitmap(outW, outH)
    val paint = Paint().apply { filterQuality = FilterQuality.None }
    val canvas = Canvas(result)

    canvas.save()
    when (orientation) {
        ExifOrientation.NORMAL -> Unit
        ExifOrientation.FLIP_HORIZONTAL -> {
            canvas.translate(w, 0f)
            canvas.scale(-1f, 1f)
        }
        ExifOrientation.ROTATE_180 -> {
            canvas.translate(w, h)
            canvas.scale(-1f, -1f)
        }
        ExifOrientation.FLIP_VERTICAL -> {
            canvas.translate(0f, h)
            canvas.scale(1f, -1f)
        }
        ExifOrientation.TRANSPOSE -> {
            canvas.scale(-1f, 1f)
            canvas.rotate(90f)
        }
        ExifOrientation.ROTATE_90 -> {
            canvas.translate(h, 0f)
            canvas.rotate(90f)
        }
        ExifOrientation.TRANSVERSE -> {
            canvas.translate(h, w)
            canvas.scale(1f, -1f)
            canvas.rotate(90f)
        }
        ExifOrientation.ROTATE_270 -> {
            canvas.translate(0f, w)
            canvas.rotate(270f)
        }
    }
    canvas.drawImage(image, Offset.Zero, paint)
    canvas.restore()

    return result
}
