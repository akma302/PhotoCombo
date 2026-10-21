package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.core.render.Border
import com.combo.core.render.TransformMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformMathTest {

    private val border = Border(100f, 100f, 200f, 100f)

    @Test
    fun `fitScale keeps whole content inside border`() {
        val scale = TransformMath.fitScale(border, Size(400f, 100f))
        val content = TransformMath.contentSize(400, 100, scale)
        assertEquals(200f, content.width, 0.001f)
        assertEquals(50f, content.height, 0.001f)
    }

    @Test
    fun `fillScale covers border`() {
        val scale = TransformMath.fillScale(border, Size(400f, 100f))
        val content = TransformMath.contentSize(400, 100, scale)
        assertEquals(100f, content.height, 0.001f)
        assertEquals(400f, content.width, 0.001f)
    }

    @Test
    fun `contentRect centers content plus pan`() {
        val rect = TransformMath.contentRect(border, 100, 100, 1f, Offset(10f, -20f))
        assertEquals(110f, rect.center.x, 0.001f)
        assertEquals(80f, rect.center.y, 0.001f)
    }

    @Test
    fun `clampPan never hides content completely`() {
        val content = Size(300f, 150f)
        val clamped = TransformMath.clampPan(Offset(1000f, -1000f), border, content)
        assertEquals((border.width + content.width) / 2, clamped.x, 0.001f)
        assertEquals(-(border.height + content.height) / 2, clamped.y, 0.001f)
        val visibleX = clamped.x + content.width / 2
        assertTrue(visibleX >= -border.width / 2)
    }

    @Test
    fun `maxContentScale caps by viewport scale`() {
        assertEquals(1.0f, TransformMath.maxContentScale(1.5f), 0.001f)
        assertEquals(0.5f, TransformMath.maxContentScale(3f), 0.001f)
    }

    @Test
    fun `border rect matches center and size`() {
        val r = border.rect()
        assertEquals(0f, r.left, 0.001f)
        assertEquals(200f, r.right, 0.001f)
        assertEquals(50f, r.top, 0.001f)
        assertEquals(150f, r.bottom, 0.001f)
    }
}
