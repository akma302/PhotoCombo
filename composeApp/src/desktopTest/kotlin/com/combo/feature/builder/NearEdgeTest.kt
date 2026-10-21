package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.combo.core.render.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Проба грани ([nearEdge]): точка, прямоугольник рамки и допуск — в ОДНИХ канвасных
 * единицах, как у `hitTest`. Экранный допуск приходит сюда поделённым на масштаб вида,
 * поэтому попадание по грани не зависит от зума; тест держит эту границу единиц измерения.
 */
class NearEdgeTest {

    private val border = Rect(100f, 50f, 800f, 450f)

    // ---------- грани ----------

    @Test
    fun `point on each border hits that border`() {
        assertEquals(Edge.LEFT, nearEdge(Offset(100f, 250f), border, 25f))
        assertEquals(Edge.RIGHT, nearEdge(Offset(800f, 250f), border, 25f))
        assertEquals(Edge.TOP, nearEdge(Offset(450f, 50f), border, 25f))
        assertEquals(Edge.BOTTOM, nearEdge(Offset(450f, 450f), border, 25f))
    }

    @Test
    fun `point within tolerance outside the border hits it`() {
        assertEquals(Edge.LEFT, nearEdge(Offset(80f, 250f), border, 25f))
        assertEquals(Edge.RIGHT, nearEdge(Offset(820f, 250f), border, 25f))
        assertEquals(Edge.TOP, nearEdge(Offset(450f, 30f), border, 25f))
        assertEquals(Edge.BOTTOM, nearEdge(Offset(450f, 470f), border, 25f))
    }

    @Test
    fun `point beyond tolerance misses`() {
        assertNull(nearEdge(Offset(70f, 250f), border, 25f))
        assertNull(nearEdge(Offset(830f, 250f), border, 25f))
        assertNull(nearEdge(Offset(450f, 20f), border, 25f))
        assertNull(nearEdge(Offset(450f, 480f), border, 25f))
    }

    /**
     * Промах далеко внутри и далеко снаружи: иначе жест у граней перехватывал бы обычный
     * сдвиг фото и панораму вида.
     */
    @Test
    fun `far point misses`() {
        assertNull(nearEdge(Offset(450f, 250f), border, 25f))
        assertNull(nearEdge(Offset(2000f, 250f), border, 25f))
    }

    /**
     * Точка в углу попадает в горизонтальную грань (приоритет левая/правая): от порядка
     * веток зависит, что ресайзится — ширина или высота.
     */
    @Test
    fun `corner point hits the horizontal border first`() {
        assertEquals(Edge.LEFT, nearEdge(Offset(105f, 55f), border, 25f))
        assertEquals(Edge.RIGHT, nearEdge(Offset(795f, 445f), border, 25f))
    }

    /**
     * Допуск и точка — в канвасных единицах: при масштабе вида ≈0.3 экранные 24 px — это
     * ≈80 единиц, и точка в 60 единицах от грани обязана попадать. Проба в экранных
     * координатах такую точку не находила.
     */
    @Test
    fun `wide tolerance covers points far from the border`() {
        assertEquals(Edge.RIGHT, nearEdge(Offset(860f, 250f), border, 80f))
        assertEquals(Edge.LEFT, nearEdge(Offset(40f, 250f), border, 80f))
    }
}
