package com.combo

import androidx.compose.ui.graphics.asSkiaBitmap
import com.combo.core.image.ExifOrientation
import com.combo.core.image.applyExifOrientation
import com.combo.core.image.exifOrientationOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пиксельная проверка всех восьми преобразований EXIF-ориентации. Неверное
 * преобразование необратимо портит фото при импорте, поэтому важна каждая ветка —
 * в том числе редкие зеркально-повёрнутые (5 и 7).
 */
class ExifOrientationTest {

    private val c0 = ImageFixtures.PATTERN_COLORS[0]
    private val c1 = ImageFixtures.PATTERN_COLORS[1]
    private val c2 = ImageFixtures.PATTERN_COLORS[2]
    private val c3 = ImageFixtures.PATTERN_COLORS[3]
    private val c4 = ImageFixtures.PATTERN_COLORS[4]
    private val c5 = ImageFixtures.PATTERN_COLORS[5]
    private val c6 = ImageFixtures.PATTERN_COLORS[6]
    private val c7 = ImageFixtures.PATTERN_COLORS[7]

    @Test
    fun `NORMAL returns the same bitmap without copying`() {
        val src = ImageFixtures.patternBitmap()

        assertSame(src, applyExifOrientation(src, ExifOrientation.NORMAL))
    }

    @Test
    fun `FLIP_HORIZONTAL mirrors left to right`() {
        assertPixels(
            ExifOrientation.FLIP_HORIZONTAL,
            listOf(
                listOf(c3, c2, c1, c0),
                listOf(c7, c6, c5, c4),
            ),
        )
    }

    @Test
    fun `ROTATE_180 turns the photo upside down`() {
        assertPixels(
            ExifOrientation.ROTATE_180,
            listOf(
                listOf(c7, c6, c5, c4),
                listOf(c3, c2, c1, c0),
            ),
        )
    }

    @Test
    fun `FLIP_VERTICAL mirrors top to bottom`() {
        assertPixels(
            ExifOrientation.FLIP_VERTICAL,
            listOf(
                listOf(c4, c5, c6, c7),
                listOf(c0, c1, c2, c3),
            ),
        )
    }

    @Test
    fun `TRANSPOSE mirrors along the main diagonal`() {
        assertPixels(
            ExifOrientation.TRANSPOSE,
            listOf(
                listOf(c0, c4),
                listOf(c1, c5),
                listOf(c2, c6),
                listOf(c3, c7),
            ),
        )
    }

    @Test
    fun `ROTATE_90 turns the photo clockwise`() {
        assertPixels(
            ExifOrientation.ROTATE_90,
            listOf(
                listOf(c4, c0),
                listOf(c5, c1),
                listOf(c6, c2),
                listOf(c7, c3),
            ),
        )
    }

    @Test
    fun `ROTATE_270 turns the photo counterclockwise`() {
        assertPixels(
            ExifOrientation.ROTATE_270,
            listOf(
                listOf(c3, c7),
                listOf(c2, c6),
                listOf(c1, c5),
                listOf(c0, c4),
            ),
        )
    }

    @Test
    fun `TRANSVERSE mirrors along the anti-diagonal`() {
        assertPixels(
            ExifOrientation.TRANSVERSE,
            listOf(
                listOf(c7, c3),
                listOf(c6, c2),
                listOf(c5, c1),
                listOf(c4, c0),
            ),
        )
    }

    @Test
    fun `exif values match the specification`() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8),
            ExifOrientation.entries.map { it.exifValue },
        )
    }

    @Test
    fun `exifOrientationOf maps known values and falls back to NORMAL`() {
        assertEquals(ExifOrientation.NORMAL, exifOrientationOf(1))
        assertEquals(ExifOrientation.TRANSPOSE, exifOrientationOf(5))
        assertEquals(ExifOrientation.ROTATE_90, exifOrientationOf(6))
        assertEquals(ExifOrientation.ROTATE_270, exifOrientationOf(8))
        assertEquals(ExifOrientation.NORMAL, exifOrientationOf(0))
        assertEquals(ExifOrientation.NORMAL, exifOrientationOf(9))
    }

    @Test
    fun `only orientations 5 to 8 swap dimensions`() {
        assertFalse(ExifOrientation.NORMAL.swapsDimensions)
        assertFalse(ExifOrientation.ROTATE_180.swapsDimensions)
        assertFalse(ExifOrientation.FLIP_VERTICAL.swapsDimensions)
        assertTrue(ExifOrientation.TRANSPOSE.swapsDimensions)
        assertTrue(ExifOrientation.ROTATE_270.swapsDimensions)
    }

    private fun assertPixels(orientation: ExifOrientation, expected: List<List<Int>>) {
        val result = applyExifOrientation(ImageFixtures.patternBitmap(), orientation)
        val expectedH = expected.size
        val expectedW = expected[0].size
        assertEquals("ширина ${orientation.name}", expectedW, result.width)
        assertEquals("высота ${orientation.name}", expectedH, result.height)

        val skia = result.asSkiaBitmap()
        for (y in 0 until expectedH) {
            for (x in 0 until expectedW) {
                assertEquals(
                    "пиксель ($x,$y) при ${orientation.name}",
                    expected[y][x],
                    skia.getColor(x, y),
                )
            }
        }
    }
}
