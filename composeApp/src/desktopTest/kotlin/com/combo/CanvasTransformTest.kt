package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.core.render.CanvasTransform
import com.combo.core.render.Viewport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CanvasTransform]: преобразование «экран ↔ холст» для редактора, плеера и списка.
 *
 * Эталонные формулы (`min(sw/cw, sh/ch)`, центрирование, сдвиг вида) считаются прямо в
 * тесте и сравниваются с результатом функции.
 */
class CanvasTransformTest {

    private val canvas = Size(1600f, 900f)

    @Test
    fun `fitInto scales to the smaller side and centres the canvas`() {
        val cases = listOf(
            Size(1000f, 600f), Size(600f, 1000f), Size(1600f, 900f), Size(10f, 5000f),
        )
        for (screen in cases) {
            val t = CanvasTransform.fitInto(canvas.width, canvas.height, screen.width, screen.height)

            val expectedScale = minOf(screen.width / canvas.width, screen.height / canvas.height)
            assertEquals("scale для $screen", expectedScale, t.scale, 1e-6f)
            assertEquals("origin.x для $screen", (screen.width - canvas.width * expectedScale) / 2f, t.origin.x, 1e-4f)
            assertEquals("origin.y для $screen", (screen.height - canvas.height * expectedScale) / 2f, t.origin.y, 1e-4f)
        }
    }

    @Test
    fun `ofViewport adds zoom and pan to the centred transform`() {
        val cases = listOf(
            Viewport(),
            Viewport(zoom = 2f, pan = Offset(30f, -40f)),
            Viewport(zoom = 0.5f, pan = Offset(-10f, 10f)),
        )
        val screens = listOf(Size(1000f, 600f), Size(600f, 1000f))
        for (viewport in cases) {
            for (screen in screens) {
                val t = CanvasTransform.ofViewport(viewport, canvas, screen)

                val expectedScale = viewport.scale(canvas, screen)
                assertEquals("scale", expectedScale, t.scale, 1e-6f)
                assertEquals(
                    "origin.x",
                    (screen.width - canvas.width * expectedScale) / 2 + viewport.pan.x,
                    t.origin.x,
                    1e-4f,
                )
                assertEquals(
                    "origin.y",
                    (screen.height - canvas.height * expectedScale) / 2 + viewport.pan.y,
                    t.origin.y,
                    1e-4f,
                )
            }
        }
    }

    @Test
    fun `toCanvas and toScreen are mutually inverse`() {
        val t = CanvasTransform.ofViewport(
            Viewport(zoom = 2.4f, pan = Offset(37f, -51f)),
            canvas,
            Size(1000f, 600f),
        )
        val points = listOf(
            Offset(0f, 0f), Offset(1600f, 900f), Offset(640f, 310f), Offset(-100f, 1200f),
        )
        for (p in points) {
            val roundTrip = t.toCanvas(t.toScreen(p))
            assertEquals("x не потерялся для $p", p.x, roundTrip.x, 1e-3f)
            assertEquals("y не потерялся для $p", p.y, roundTrip.y, 1e-3f)
        }
    }

    @Test
    fun `canvas centre lands in the middle of the screen when there is no pan`() {
        val screen = Size(1000f, 600f)
        val t = CanvasTransform.ofViewport(Viewport(), canvas, screen)
        val centreOnScreen = t.toScreen(Offset(canvas.width / 2, canvas.height / 2))
        assertEquals(screen.width / 2, centreOnScreen.x, 1e-3f)
        assertEquals(screen.height / 2, centreOnScreen.y, 1e-3f)
    }
}
