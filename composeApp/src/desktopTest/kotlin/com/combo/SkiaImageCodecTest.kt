package com.combo

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageFormat
import com.combo.core.image.SkiaImageCodec
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.math.abs

/**
 * Кодек на настоящих файлах во временном каталоге: чтение границ и EXIF, сэмпленное
 * декодирование (встроенное у JPEG, локальный фолбэк у PNG), терпимость к мусору.
 */
class SkiaImageCodecTest {

    private lateinit var dir: java.io.File
    private val files = FileSystem.SYSTEM
    private lateinit var codec: SkiaImageCodec

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("photocombo-codec-test").toFile()
        codec = SkiaImageCodec(files, FakeLogger())
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `readBounds returns the stored size for jpeg and png`() {
        val jpeg = write("a.jpg", ImageFixtures.jpegBytes(ImageFixtures.patternBitmap()))
        val png = write("a.png", ImageFixtures.pngBytes(ImageFixtures.patternBitmap()))

        assertEquals(IntSize(4, 2), codec.readBounds(jpeg))
        assertEquals(IntSize(4, 2), codec.readBounds(png))
    }

    @Test
    fun `readOrientation returns each injected exif value`() {
        for (value in 1..8) {
            val file = write("o$value.jpg", ImageFixtures.jpegWithOrientation(value))

            assertEquals(
                "EXIF $value",
                ExifOrientation.entries[value - 1],
                codec.readOrientation(file),
            )
        }
    }

    @Test
    fun `a jpeg without exif reads as normal`() {
        val file = write("plain.jpg", ImageFixtures.jpegBytes(ImageFixtures.patternBitmap()))

        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(file))
    }

    @Test
    fun `decode does not apply exif orientation itself`() {
        val file = write("rot6.jpg", ImageFixtures.jpegWithOrientation(6))

        val decoded = codec.decode(file)!!

        assertEquals(IntSize(4, 2), IntSize(decoded.width, decoded.height))
    }

    @Test
    fun `decode with sample downscales a jpeg in the codec`() {
        val file = write("big.jpg", ImageFixtures.jpegBytes(ImageFixtures.solidBitmap(256, 128, ImageFixtures.RED)))

        val decoded = codec.decode(file, sample = 2)!!

        assertEquals(IntSize(128, 64), IntSize(decoded.width, decoded.height))
        assertColorNear(ImageFixtures.RED, decoded.asSkiaBitmap().getColor(64, 32))
    }

    @Test
    fun `decode with sample downscales a png through the local fallback`() {
        val file = write("big.png", ImageFixtures.pngBytes(ImageFixtures.solidBitmap(256, 128, ImageFixtures.RED)))

        val decoded = codec.decode(file, sample = 2)!!

        assertEquals(IntSize(128, 64), IntSize(decoded.width, decoded.height))
        assertEquals(ImageFixtures.RED, decoded.asSkiaBitmap().getColor(64, 32))
    }

    @Test
    fun `decode without sample keeps the original size`() {
        val file = write("solid.png", ImageFixtures.pngBytes(ImageFixtures.solidBitmap(32, 16, ImageFixtures.BLUE)))

        val decoded = codec.decode(file)!!

        assertEquals(IntSize(32, 16), IntSize(decoded.width, decoded.height))
        assertEquals(ImageFixtures.BLUE, decoded.asSkiaBitmap().getColor(16, 8))
    }

    @Test
    fun `decode reads webp images`() {
        val file = write(
            "photo.webp",
            ImageFixtures.webpBytes(ImageFixtures.solidBitmap(32, 16, ImageFixtures.BLUE)),
        )

        assertEquals(IntSize(32, 16), codec.readBounds(file))
        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(file))
        val decoded = codec.decode(file)!!
        assertEquals(IntSize(32, 16), IntSize(decoded.width, decoded.height))
    }

    @Test
    fun `garbage bytes are not an image`() {
        val file = write("garbage.jpg", "definitely not an image".toByteArray())

        assertNull(codec.readBounds(file))
        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(file))
        assertNull(codec.decode(file))
    }

    @Test
    fun `a missing file is not an image`() {
        val file = path("missing.jpg")

        assertNull(codec.readBounds(file))
        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(file))
        assertNull(codec.decode(file))
    }

    @Test
    fun `encode writes files that decode back`() {
        val png = path("out.png")
        val jpeg = path("out.jpg")
        val halves = ImageFixtures.halvesBitmap(16, 8)

        assertTrue(codec.encode(png, halves, ImageFormat.PNG, 100))
        assertTrue(codec.encode(jpeg, halves, ImageFormat.JPEG, 100))

        assertEquals(IntSize(16, 8), codec.readBounds(png))
        assertEquals(IntSize(16, 8), codec.readBounds(jpeg))
        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(png))
        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(jpeg))

        val pngDecoded = codec.decode(png)!!
        assertEquals(ImageFixtures.RED, pngDecoded.asSkiaBitmap().getColor(0, 0))
        assertEquals(ImageFixtures.BLUE, pngDecoded.asSkiaBitmap().getColor(15, 7))

        val jpegDecoded = codec.decode(jpeg)!!
        assertColorNear(ImageFixtures.RED, jpegDecoded.asSkiaBitmap().getColor(0, 0))
        assertColorNear(ImageFixtures.BLUE, jpegDecoded.asSkiaBitmap().getColor(15, 7))
    }

    private fun path(name: String): Path = java.io.File(dir, name).toOkioPath()

    private fun write(name: String, bytes: ByteArray): Path =
        path(name).also { files.write(it) { write(bytes) } }

    /** JPEG сжимает с потерями: канал считается совпавшим, если ошибка в пределах допуска. */
    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int = 24) {
        assertEquals("непрозрачность", 0xFF, actual ushr 24)
        for (shift in intArrayOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(
                "канал со сдвигом $shift: ожидали ~$e, получили $a",
                abs(e - a) <= tolerance,
            )
        }
    }
}
