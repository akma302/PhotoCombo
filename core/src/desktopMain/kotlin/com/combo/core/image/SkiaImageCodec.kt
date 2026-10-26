package com.combo.core.image

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.combo.core.platform.Logger
import okio.FileSystem
import okio.IOException
import okio.Path
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.use

private const val TAG = "SkiaImageCodec"

/** Уровень сжатия PNG при перекодировании; баланс скорость/размер. */
private const val PNG_COMPRESSION_LEVEL = 6

/**
 * Кодек на Skia: одна реализация на все платформы, где Skia доступна. Декодирование
 * с `sample > 1` выполняется средствами кодека (JPEG умеет отдавать уменьшенные кадры
 * без полного декода); если кодек так не умеет (PNG, WebP), кадр декодируется целиком
 * и масштабируется локально.
 */
class SkiaImageCodec(
    private val files: FileSystem,
    private val logger: Logger,
) : ImageCodec {

    override fun readBounds(path: Path): IntSize? = withCodec(path) { codec ->
        IntSize(codec.imageInfo.width, codec.imageInfo.height)
    }

    override fun readOrientation(path: Path): ExifOrientation {
        val orientation = try {
            withCodec(path) { codec -> codec.encodedOrigin.toExifOrientation() }
        } catch (e: Exception) {
            logger.warn(TAG, "Failed to read EXIF orientation for ${path.name}", e)
            return ExifOrientation.NORMAL
        }
        return orientation ?: ExifOrientation.NORMAL
    }

    override fun decode(path: Path, sample: Int): ImageBitmap? {
        val s = sample.coerceAtLeast(1)
        return try {
            withCodec(path) { codec ->
                val srcW = codec.imageInfo.width
                val srcH = codec.imageInfo.height
                if (srcW <= 0 || srcH <= 0) return@withCodec null

                if (s <= 1) return@withCodec readPixels(codec, srcW, srcH)

                val targetW = (srcW + s - 1) / s
                val targetH = (srcH + s - 1) / s
                readPixels(codec, targetW, targetH)
                    ?: scaleDown(codec, srcW, srcH, targetW, targetH)
            }
        } catch (e: Exception) {
            logger.warn(TAG, "Failed to decode ${path.name}", e)
            null
        }
    }

    override fun encode(path: Path, image: ImageBitmap, format: ImageFormat, quality: Int): Boolean = try {
        val skiaFormat = when (format) {
            ImageFormat.JPEG -> EncodedImageFormat.JPEG
            ImageFormat.PNG -> EncodedImageFormat.PNG
        }
        val skiaImage = Image.makeFromBitmap(image.asSkiaBitmap())
        val data = skiaImage.use { it.encodeToData(skiaFormat, quality.coerceIn(0, 100), PNG_COMPRESSION_LEVEL) }
        if (data == null) {
            logger.warn(TAG, "Encode failed for ${path.name}")
            false
        } else {
            data.use { encoded -> files.write(path) { write(encoded.bytes) } }
            true
        }
    } catch (e: Exception) {
        logger.warn(TAG, "Failed to encode ${path.name}", e)
        false
    }

    /**
     * Читает файл и открывает кодек. `null` — файл не читается или не является
     * изображением: и то и другое для вызывающего одна и та же ситуация.
     */
    private inline fun <T> withCodec(path: Path, block: (Codec) -> T): T? = try {
        val bytes = files.read(path) { readByteArray() }
        Codec.makeFromData(Data.makeFromBytes(bytes)).use { block(it) }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Декодирует кадр размера [width] x [height]. Кодек сам вписывает изображение
     * в этот размер, если умеет; если нет — бросает [IllegalArgumentException],
     * и вызывающий пробует другой путь.
     */
    private fun readPixels(codec: Codec, width: Int, height: Int): ImageBitmap? = try {
        val bitmap = Bitmap()
        if (!bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL))) {
            null
        } else {
            codec.readPixels(bitmap)
            bitmap.asComposeImageBitmap()
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Полный декод + локальное масштабирование: для кодеков без встроенного сэмплинга. */
    private fun scaleDown(codec: Codec, srcW: Int, srcH: Int, targetW: Int, targetH: Int): ImageBitmap? {
        val full = readPixels(codec, srcW, srcH) ?: return null
        val scaled = ImageBitmap(targetW, targetH)
        val paint = Paint().apply { filterQuality = FilterQuality.Medium }
        Canvas(scaled).drawImageRect(
            full,
            IntOffset.Zero,
            IntSize(srcW, srcH),
            IntOffset.Zero,
            IntSize(targetW, targetH),
            paint,
        )
        return scaled
    }
}

private fun EncodedOrigin.toExifOrientation(): ExifOrientation = when (this) {
    EncodedOrigin.TOP_LEFT -> ExifOrientation.NORMAL
    EncodedOrigin.TOP_RIGHT -> ExifOrientation.FLIP_HORIZONTAL
    EncodedOrigin.BOTTOM_RIGHT -> ExifOrientation.ROTATE_180
    EncodedOrigin.BOTTOM_LEFT -> ExifOrientation.FLIP_VERTICAL
    EncodedOrigin.LEFT_TOP -> ExifOrientation.TRANSPOSE
    EncodedOrigin.RIGHT_TOP -> ExifOrientation.ROTATE_90
    EncodedOrigin.RIGHT_BOTTOM -> ExifOrientation.TRANSVERSE
    EncodedOrigin.LEFT_BOTTOM -> ExifOrientation.ROTATE_270
    // UNUSED и всё, что Skia добавит позже, означает «ориентации нет».
    else -> ExifOrientation.NORMAL
}
