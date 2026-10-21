package com.combo

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.combo.core.util.AverageColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AverageColorTest {

    private fun bitmap(w: Int, h: Int, draw: Canvas.() -> Unit): ImageBitmap {
        val image = ImageBitmap(w, h)
        Canvas(image).draw()
        return image
    }

    private fun Canvas.fill(color: Color, left: Float, top: Float, right: Float, bottom: Float, mode: BlendMode = BlendMode.SrcOver) {
        drawRect(left, top, right, bottom, Paint().also { it.color = color; it.blendMode = mode })
    }

    private fun channels(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun assertNear(expected: Int, actual: Int, what: String) =
        assertTrue("$what: ожидалось $expected±1, получено $actual", abs(expected - actual) <= 1)

    @Test
    fun `a solid image averages to its own color and is opaque`() {
        val image = bitmap(4, 4) { fill(Color(255, 128, 0), 0f, 0f, 4f, 4f, BlendMode.Src) }
        val (r, g, b) = channels(AverageColor.of(image))
        assertNear(255, r, "R"); assertNear(128, g, "G"); assertNear(0, b, "B")
        assertEquals(0xFF, AverageColor.of(image) ushr 24)
    }

    @Test
    fun `two equal halves average to the midpoint`() {
        val image = bitmap(4, 4) {
            fill(Color(255, 0, 0), 0f, 0f, 2f, 4f, BlendMode.Src)
            fill(Color(0, 0, 255), 2f, 0f, 4f, 4f, BlendMode.Src)
        }
        val (r, g, b) = channels(AverageColor.of(image))
        assertNear(128, r, "R"); assertNear(0, g, "G"); assertNear(128, b, "B")
    }

    @Test
    fun `transparent pixels do not darken the average`() {
        val image = bitmap(4, 4) {
            fill(Color(0, 255, 0), 0f, 0f, 2f, 4f, BlendMode.Src)
            fill(Color(0, 0, 0, 0), 2f, 0f, 4f, 4f, BlendMode.Src)
        }
        val (r, g, b) = channels(AverageColor.of(image))
        assertNear(0, r, "R"); assertNear(255, g, "G"); assertNear(0, b, "B")
    }
}
