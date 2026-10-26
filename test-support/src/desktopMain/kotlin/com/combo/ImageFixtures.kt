package com.combo

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use

/**
 * Синтетические изображения для тестов: кадры из различимых цветов и JPEG с внедрённым
 * EXIF-тегом Orientation. Настоящие фотографии с камер для регулярных тестов не нужны —
 * их импорт проверяет отдельный тест по явно заданному каталогу.
 */
object ImageFixtures {

    const val PATTERN_W = 4
    const val PATTERN_H = 2

    val RED: Int = 0xFFFF0000.toInt()
    val BLUE: Int = 0xFF0000FF.toInt()

    /**
     * Восемь насыщенных цветов, расположенных сеткой [PATTERN_W] x [PATTERN_H]; цвет
     * пикселя (x, y) — `PATTERN_COLORS[y * PATTERN_W + x]`. Цвета далеки друг от друга,
     * чтобы перепутанные ветки преобразований были видны сразу.
     */
    val PATTERN_COLORS = intArrayOf(
        0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt(),
        0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
    )

    fun patternBitmap(): ImageBitmap = bitmap(PATTERN_W, PATTERN_H) { canvas ->
        for (y in 0 until PATTERN_H) {
            for (x in 0 until PATTERN_W) {
                fill(canvas, x, y, PATTERN_COLORS[y * PATTERN_W + x])
            }
        }
    }

    /**
     * Кадр из двух половин: левая красная, правая синяя. Границы блоков JPEG проходят
     * на смене цвета, поэтому углы после поворота остаются насыщенными.
     */
    fun halvesBitmap(width: Int = 8, height: Int = 4): ImageBitmap = bitmap(width, height) { canvas ->
        for (y in 0 until height) {
            for (x in 0 until width) {
                fill(canvas, x, y, if (x < width / 2) RED else BLUE)
            }
        }
    }

    fun solidBitmap(width: Int, height: Int, color: Int): ImageBitmap = bitmap(width, height) { canvas ->
        for (y in 0 until height) {
            for (x in 0 until width) {
                fill(canvas, x, y, color)
            }
        }
    }

    fun bitmap(width: Int, height: Int, draw: (Canvas) -> Unit): ImageBitmap {
        val image = ImageBitmap(width, height)
        draw(Canvas(image))
        return image
    }

    fun jpegBytes(image: ImageBitmap, quality: Int = 100): ByteArray =
        encode(image, EncodedImageFormat.JPEG, quality)

    fun pngBytes(image: ImageBitmap): ByteArray = encode(image, EncodedImageFormat.PNG, 100)

    fun webpBytes(image: ImageBitmap): ByteArray = encode(image, EncodedImageFormat.WEBP, 90)

    /** JPEG с внедрённым тегом Orientation; пиксели при этом не повёрнуты. */
    fun jpegWithOrientation(orientation: Int, image: ImageBitmap = patternBitmap()): ByteArray {
        val jpeg = jpegBytes(image)
        val exif = exifApp1(orientation)
        return jpeg.copyOfRange(0, 2) + exif + jpeg.copyOfRange(2, jpeg.size)
    }

    /**
     * APP1-сегмент с EXIF: "Exif\0\0" + минимальный TIFF (little-endian, одна запись IFD
     * с тегом 0x0112 Orientation, тип SHORT, количество 1).
     */
    fun exifApp1(orientation: Int): ByteArray {
        val tiff = ByteArray(26)
        tiff[0] = 'I'.code.toByte()
        tiff[1] = 'I'.code.toByte()
        tiff[2] = 42
        tiff[4] = 8
        tiff[8] = 1
        tiff[10] = 0x12.toByte()
        tiff[11] = 0x01
        tiff[12] = 3
        tiff[14] = 1
        tiff[18] = orientation.toByte()
        val payload = "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        return byteArrayOf(
            0xFF.toByte(),
            0xE1.toByte(),
            (length shr 8).toByte(),
            (length and 0xFF).toByte(),
        ) + payload
    }

    private fun fill(canvas: Canvas, x: Int, y: Int, color: Int) {
        canvas.drawRect(
            x.toFloat(),
            y.toFloat(),
            (x + 1).toFloat(),
            (y + 1).toFloat(),
            Paint().apply {
                this.color = Color(color)
                blendMode = BlendMode.Src
            },
        )
    }

    private fun encode(image: ImageBitmap, format: EncodedImageFormat, quality: Int): ByteArray {
        val skiaImage = Image.makeFromBitmap(image.asSkiaBitmap())
        val data = skiaImage.use { it.encodeToData(format, quality, 6) } ?: error("encode failed")
        return data.use { it.bytes }
    }
}
