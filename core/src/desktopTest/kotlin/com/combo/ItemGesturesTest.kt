package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.core.render.Border
import com.combo.core.render.ItemDraft
import com.combo.core.render.ItemGestures
import com.combo.core.render.TransformMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemGesturesTest {

    /** Рамка с одним фото `p1`: масштаб и сдвиг фото — параметры. */
    private fun item(
        w: Float = 200f,
        h: Float = 100f,
        scale: Float = 1f,
        pan: Offset = Offset.Zero,
    ) = ItemDraft(
        id = "i1",
        zIndex = 0,
        centerX = 100f,
        centerY = 100f,
        width = w,
        height = h,
        photoId = "p1",
        contentScale = scale,
        panX = pan.x,
        panY = pan.y,
    )

    // ---------- зум фото: границы масштаба ----------

    @Test
    fun `zoomContent does not throw when min scale is above cap`() {
        // Рамка намного больше фото: fitScale*0.25 > maxContentScale(vs), и
        // coerceIn(min, max) с min > max бросил бы IllegalArgumentException.
        val b = Border(100f, 100f, 4000f, 4000f)
        val src = Size(10f, 10f)
        val c = item(scale = 0.5f)
        ItemGestures.zoomContent(c, 1.2f, b, src, vs = 4f)
        assertTrue(c.contentScale > 0f)
    }

    @Test
    fun `zoom in never shrinks content above cap`() {
        val b = Border(100f, 100f, 200f, 100f)
        val src = Size(400f, 400f)
        val cap = TransformMath.maxContentScale(1f)
        val c = item(scale = cap * 2f) // выше потолка (например, после zoom вида)
        ItemGestures.zoomContent(c, 1.5f, b, src, vs = 1f)
        assertEquals("zoom-in не должен уменьшать масштаб", cap * 2f, c.contentScale, 0.0001f)
    }

    @Test
    fun `zoom in grows content up to cap`() {
        val b = Border(100f, 100f, 200f, 100f)
        val src = Size(400f, 400f)
        val c = item(scale = 0.1f)
        ItemGestures.zoomContent(c, 2f, b, src, vs = 1f)
        assertEquals(0.2f, c.contentScale, 0.0001f)

        ItemGestures.zoomContent(c, 100f, b, src, vs = 1f)
        assertEquals(TransformMath.maxContentScale(1f), c.contentScale, 0.0001f)
    }

    @Test
    fun `zoom out never drops content below min`() {
        val b = Border(100f, 100f, 200f, 100f)
        val src = Size(400f, 400f)
        val min = TransformMath.fitScale(b, src) * 0.25f
        val c = item(scale = min * 2f)
        ItemGestures.zoomContent(c, 0.001f, b, src, vs = 1f)
        assertEquals(min, c.contentScale, 0.0001f)
    }

    // ---------- щипок рамки ----------

    @Test
    fun `pinch keeps content position relative to frame`() {
        val it = item(scale = 1.5f, pan = Offset(30f, -12f))
        val b = it.border()

        ItemGestures.pinchItem(it, 2f, minSize = 8f)

        // Доля расстояния от центра рамки до центра фото не должна измениться.
        val before = TransformMath.contentRect(b, 100, 100, it.contentScale, Offset(30f, -12f))
        val relativeBefore = Offset(
            (before.center.x - b.centerX) / b.width,
            (before.center.y - b.centerY) / b.height,
        )
        val b2 = it.border()
        val after = TransformMath.contentRect(b2, 100, 100, it.contentScale, Offset(it.panX, it.panY))
        val relativeAfter = Offset(
            (after.center.x - b2.centerX) / b2.width,
            (after.center.y - b2.centerY) / b2.height,
        )
        assertEquals(relativeBefore.x, relativeAfter.x, 0.0001f)
        assertEquals(relativeBefore.y, relativeAfter.y, 0.0001f)
        assertEquals(3f, it.contentScale, 0.0001f)
    }

    @Test
    fun `pinch below min size changes nothing`() {
        val it = item(w = 100f, h = 100f, scale = 1f, pan = Offset(10f, 10f))
        ItemGestures.pinchItem(it, 0.01f, minSize = 8f)
        assertEquals(100f, it.width, 0.0001f)
        assertEquals(1f, it.contentScale, 0.0001f)
        assertEquals(10f, it.panX, 0.0001f)
    }
}