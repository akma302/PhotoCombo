package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.Const
import com.combo.core.render.Viewport
import com.combo.core.render.viewportZoomPan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Зум вида «вокруг точки» ([zoomedAround]).
 *
 * Главное проверяемое свойство то же, что и в [ViewportTest]: точка холста под центром
 * пальцев остаётся на месте. Оно нарушается при наивном «просто умножить zoom», поэтому
 * панорама в тестах всегда ненулевая, а фокус взят не в центре холста.
 */
class ViewportControllerTest {

    private val canvas = Size(1600f, 900f)
    private val screen = Size(1000f, 600f)
    private val focus = Offset(640f, 310f)

    /** Экранное положение левого верхнего угла холста (та же формула, что в [zoomedAround]). */
    private fun originOf(v: Viewport): Offset {
        val vs = v.scale(canvas, screen)
        return Offset(
            (screen.width - canvas.width * vs) / 2f + v.pan.x,
            (screen.height - canvas.height * vs) / 2f + v.pan.y,
        )
    }

    /** Точка холста под экранной точкой `p`: `(p - origin) / vs`. */
    private fun canvasPointUnder(p: Offset, v: Viewport): Offset {
        val vs = v.scale(canvas, screen)
        val o = originOf(v)
        return Offset((p.x - o.x) / vs, (p.y - o.y) / vs)
    }

    private fun assertFocusHeld(before: Viewport, after: Viewport, factor: Float) {
        val p0 = canvasPointUnder(focus, before)
        val p1 = canvasPointUnder(focus, after)
        assertEquals("x под фокусом при factor=$factor", p0.x, p1.x, 1e-2f)
        assertEquals("y под фокусом при factor=$factor", p0.y, p1.y, 1e-2f)
    }

    @Test
    fun `canvas point under the focus does not move when zooming in`() {
        for (zoom in listOf(1.2f, 2.1f)) {
            val before = Viewport(zoom = zoom, pan = Offset(37f, -51f))
            val after = before.zoomedAround(1.8f, focus, screen, canvas)
            assertEquals("зум обязан примениться целиком", zoom * 1.8f, after.zoom, 1e-4f)
            assertFocusHeld(before, after, 1.8f)
        }
    }

    @Test
    fun `canvas point under the focus does not move when zooming out`() {
        for (zoom in listOf(2.4f, 3.9f)) {
            val before = Viewport(zoom = zoom, pan = Offset(37f, -51f))
            val after = before.zoomedAround(0.6f, focus, screen, canvas)
            assertEquals(zoom * 0.6f, after.zoom, 1e-4f)
            assertFocusHeld(before, after, 0.6f)
        }
    }

    /** Зум ровно в центре холста на экране симметричен, поэтому вид не должен сдвинуться. */
    @Test
    fun `zooming around the canvas centre keeps the pan`() {
        val before = Viewport(zoom = 1f, pan = Offset.Zero)
        val o = originOf(before)
        val vs = before.scale(canvas, screen)
        val centre = Offset(o.x + canvas.width * vs / 2f, o.y + canvas.height * vs / 2f)

        val after = before.zoomedAround(2f, centre, screen, canvas)

        assertEquals(2f, after.zoom, 1e-4f)
        assertEquals("сдвиг вида при зуме от центра должен остаться нулевым", 0f, after.pan.x, 1e-3f)
        assertEquals(0f, after.pan.y, 1e-3f)
    }

    // ---------- границы ----------

    @Test
    fun `a huge factor lands exactly on VIEW_ZOOM_MAX`() {
        val before = Viewport(zoom = 1f, pan = Offset(37f, -51f))

        val after = before.zoomedAround(1000f, focus, screen, canvas)

        assertEquals(Const.VIEW_ZOOM_MAX, after.zoom, 0f)
    }

    @Test
    fun `a tiny factor lands exactly on VIEW_ZOOM_MIN`() {
        val before = Viewport(zoom = 1f, pan = Offset(37f, -51f))

        val after = before.zoomedAround(0.0001f, focus, screen, canvas)

        assertEquals(Const.VIEW_ZOOM_MIN, after.zoom, 0f)
    }

    /**
     * У границы панорама обязана считаться от ПРИМЕНЁННОГО фактора. Проверяем эталонным
     * пересчётом [viewportZoomPan] от `VIEW_ZOOM_MAX / старый зум` — если бы считалось от
     * запрошенного фактора (1000), pan улетел бы на десятки экранов, и холст дёрнулся бы
     * на последнем кадре жеста.
     */
    @Test
    fun `at the upper bound the pan follows the applied factor`() {
        val before = Viewport(zoom = 1.5f, pan = Offset(37f, -51f))

        val after = before.zoomedAround(1000f, focus, screen, canvas)

        val applied = Const.VIEW_ZOOM_MAX / before.zoom
        val expected = viewportZoomPan(
            focus = focus,
            origin = originOf(before),
            factor = applied,
            screen = screen,
            canvas = canvas,
            vs = before.scale(canvas, screen),
        )
        assertEquals("pan x обязан считаться от применённого фактора", expected.x, after.pan.x, 1e-3f)
        assertEquals("pan y обязан считаться от применённого фактора", expected.y, after.pan.y, 1e-3f)
        // И главное: раз панорама посчитана от применённого фактора, точка холста под
        // фокусом не «уезжает» даже при клампе — она просто не доезжает до запрошенного.
        assertFocusHeld(before, after, 1000f)
    }

    @Test
    fun `at the lower bound the pan follows the applied factor`() {
        val before = Viewport(zoom = 2.6f, pan = Offset(-120f, 64f))

        val after = before.zoomedAround(0.0001f, focus, screen, canvas)

        val applied = Const.VIEW_ZOOM_MIN / before.zoom
        val expected = viewportZoomPan(
            focus = focus,
            origin = originOf(before),
            factor = applied,
            screen = screen,
            canvas = canvas,
            vs = before.scale(canvas, screen),
        )
        assertEquals(expected.x, after.pan.x, 1e-3f)
        assertEquals(expected.y, after.pan.y, 1e-3f)
        assertFocusHeld(before, after, 0.0001f)
    }

    /** На наборе множителей зум обязан оставаться в границах (зажим не пропущен). */
    @Test
    fun `zoom always stays inside the allowed range`() {
        for (zoom in listOf(1f, 1.7f, 3f)) {
            for (factor in listOf(0.0001f, 0.5f, 1f, 3.7f, 1000f)) {
                val after = Viewport(zoom = zoom, pan = Offset(11f, -13f)).zoomedAround(factor, focus, screen, canvas)
                assertTrue(
                    "zoom=${after.zoom} вне границ при zoom=$zoom, factor=$factor",
                    after.zoom in Const.VIEW_ZOOM_MIN..Const.VIEW_ZOOM_MAX,
                )
            }
        }
    }

    // ---------- нулевой фактор ----------

    /**
     * Нулевой множитель не даёт деления на ноль: [zoomedAround] делит на СТАРЫЙ зум
     * (`applied = zoom / this.zoom`). Зум уходит на нижнюю границу, а не превращается в NaN.
     */
    @Test
    fun `a zero factor at the lower bound changes nothing`() {
        val before = Viewport(zoom = Const.VIEW_ZOOM_MIN, pan = Offset(37f, -51f))

        val after = before.zoomedAround(0f, focus, screen, canvas)

        assertEquals("зум уже на границе и ниже не опускается", Const.VIEW_ZOOM_MIN, after.zoom, 0f)
        assertEquals("applied == 1, поэтому вид не должен сдвинуться", before.pan.x, after.pan.x, 1e-3f)
        assertEquals(before.pan.y, after.pan.y, 1e-3f)
    }

    @Test
    fun `a zero factor from the default zoom gives a finite viewport`() {
        val before = Viewport(zoom = 1f, pan = Offset(37f, -51f))

        val after = before.zoomedAround(0f, focus, screen, canvas)

        assertEquals("0 * 1 = 0, зажим поднимает зум до нижней границы", Const.VIEW_ZOOM_MIN, after.zoom, 0f)
        assertTrue("pan x не должен стать NaN или бесконечностью", after.pan.x.isFinite())
        assertTrue("pan y не должен стать NaN или бесконечностью", after.pan.y.isFinite())
    }

    /**
     * Границы зума — часть поведения (политика из [Const]): `0.5` и `4`. Тест ломается, если
     * значения изменят без осознанного решения.
     */
    @Test
    fun `the zoom bounds keep their specified values`() {
        assertEquals(0.5f, Const.VIEW_ZOOM_MIN, 0f)
        assertEquals(4f, Const.VIEW_ZOOM_MAX, 0f)
    }

    /** Исходный вид не мутируется: результат — новый объект (вид в undo не входит). */
    @Test
    fun `the source viewport is left untouched`() {
        val before = Viewport(zoom = 1f, pan = Offset(37f, -51f))

        before.zoomedAround(2f, focus, screen, canvas)

        assertEquals(1f, before.zoom, 0f)
        assertEquals(Offset(37f, -51f), before.pan)
    }
}