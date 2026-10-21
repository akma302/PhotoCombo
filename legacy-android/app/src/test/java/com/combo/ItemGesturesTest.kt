package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.core.render.Border
import com.combo.core.render.ContentDraft
import com.combo.core.render.ItemDraft
import com.combo.core.render.ItemGestures
import com.combo.core.render.TransformMath
import com.combo.core.render.isVerticalFling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemGesturesTest {

    // ---------- порог флинга ----------

    /**
     * Порог живёт в `Const`, а сравнение — в чистой функции: иначе у билдера и плеера
     * были свои числа (800/1.5), и «смена фото по свайпу» работала в одном экране и не
     * работала в другом.
     */
    @Test
    fun `slow swipe is not a vertical fling`() {
        assertFalse("1500 px/с — ниже порога", isVerticalFling(0f, 1500f))
        assertFalse("вниз тоже не флинг при малой скорости", isVerticalFling(0f, -1500f))
    }

    @Test
    fun `fast vertical swipe is a fling`() {
        assertTrue(isVerticalFling(0f, 1700f))
        assertTrue("вниз — тоже флинг", isVerticalFling(0f, -1700f))
        assertTrue("небольшая горизонтальная составляющая допустима", isVerticalFling(700f, 1700f))
    }

    @Test
    fun `diagonal swipe below the dominance ratio is not a fling`() {
        // vy = 1800 проходит порог скорости, но 1800 < 1000 * 2 — это диагональ, а не
        // «смена фото»: иначе панорамирование контента постоянно переключало бы фото.
        assertFalse(isVerticalFling(1000f, 1800f))
        // Граница строгая: ровно двойное доминирование — ещё не флинг.
        assertFalse("ровно 2x — не флинг", isVerticalFling(850f, 1700f))
        assertTrue("чуть больше 2x — уже флинг", isVerticalFling(840f, 1700f))
    }

    private fun content(scale: Float = 1f, pan: Offset = Offset.Zero) =
        ContentDraft("c1", "p1", 0, scale, pan.x, pan.y)

    private fun item(w: Float = 200f, h: Float = 100f, vararg contents: ContentDraft) = ItemDraft(
        id = "i1",
        zIndex = 0,
        centerX = 100f,
        centerY = 100f,
        width = w,
        height = h,
        contents = contents.toMutableList(),
    )

    // ---------- зум фото: границы масштаба ----------

    @Test
    fun `zoomContent does not throw when min scale is above cap`() {
        // Рамка намного больше фото: fitScale*0.25 > maxContentScale(vs), и
        // coerceIn(min, max) с min > max бросил бы IllegalArgumentException.
        val b = Border(100f, 100f, 4000f, 4000f)
        val src = Size(10f, 10f)
        val c = content(scale = 0.5f)
        ItemGestures.zoomContent(c, 1.2f, b, src, vs = 4f)
        assertTrue(c.contentScale > 0f)
    }

    @Test
    fun `zoom in never shrinks content above cap`() {
        val b = Border(100f, 100f, 200f, 100f)
        val src = Size(400f, 400f)
        val cap = TransformMath.maxContentScale(1f)
        val c = content(scale = cap * 2f) // выше потолка (например, после zoom вида)
        ItemGestures.zoomContent(c, 1.5f, b, src, vs = 1f)
        assertEquals("zoom-in не должен уменьшать масштаб", cap * 2f, c.contentScale, 0.0001f)
    }

    @Test
    fun `zoom in grows content up to cap`() {
        val b = Border(100f, 100f, 200f, 100f)
        val src = Size(400f, 400f)
        val c = content(scale = 0.1f)
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
        val c = content(scale = min * 2f)
        ItemGestures.zoomContent(c, 0.001f, b, src, vs = 1f)
        assertEquals(min, c.contentScale, 0.0001f)
    }

    // ---------- щипок рамки ----------

    @Test
    fun `pinch keeps content position relative to frame`() {
        val c = content(scale = 1.5f, pan = Offset(30f, -12f))
        val it = item(contents = arrayOf(c))
        val b = it.border()

        ItemGestures.pinchItem(it, 2f, minSize = 8f)

        // Доля расстояния от центра рамки до центра фото не должна измениться.
        val before = TransformMath.contentRect(b, 100, 100, c.contentScale, Offset(30f, -12f))
        val relativeBefore = Offset(
            (before.center.x - b.centerX) / b.width,
            (before.center.y - b.centerY) / b.height,
        )
        val c2 = it.contents.single()
        val b2 = it.border()
        val after = TransformMath.contentRect(b2, 100, 100, c2.contentScale, Offset(c2.panX, c2.panY))
        val relativeAfter = Offset(
            (after.center.x - b2.centerX) / b2.width,
            (after.center.y - b2.centerY) / b2.height,
        )
        assertEquals(relativeBefore.x, relativeAfter.x, 0.0001f)
        assertEquals(relativeBefore.y, relativeAfter.y, 0.0001f)
        assertEquals(3f, c2.contentScale, 0.0001f)
    }

    @Test
    fun `pinch below min size changes nothing`() {
        val c = content(scale = 1f, pan = Offset(10f, 10f))
        val it = item(w = 100f, h = 100f, contents = arrayOf(c))
        ItemGestures.pinchItem(it, 0.01f, minSize = 8f)
        assertEquals(100f, it.width, 0.0001f)
        assertEquals(1f, c.contentScale, 0.0001f)
        assertEquals(10f, c.panX, 0.0001f)
    }
}