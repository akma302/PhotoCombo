package com.combo

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.combo.core.background.ResolvedBackground
import com.combo.core.model.FramingMode
import com.combo.core.render.Border
import com.combo.core.render.CanvasTransform
import com.combo.core.render.FrameContent
import com.combo.core.render.Viewport
import com.combo.core.render.buildItemFrame
import com.combo.core.render.drawCombo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Единая отрисовка комбо обрезает всё по прямоугольнику холста: рамка, свисающая за край,
 * не оставляет пикселей за его пределами. Правило действует сразу в редакторе, превью
 * списка, потому что рисуют они одним вызовом [drawCombo].
 */
class DrawComboClipTest {

    private fun rasterize(
        w: Int,
        h: Int,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = Size(w.toFloat(), h.toFloat()),
            block = block,
        )
        return image
    }

    private fun ImageBitmap.pixel(x: Int, y: Int): Int = asSkiaBitmap().getColor(x, y)

    private fun alphaOf(argb: Int) = argb ushr 24

    @Test
    fun `frame hanging over the canvas edge is clipped`() {
        val photo = ImageFixtures.solidBitmap(100, 100, 0xFFFF0000.toInt())
        // Холст 100×100 вписан в поверхность 200×100 по центру: экранные x 50..150.
        val transform = CanvasTransform.ofViewport(Viewport(), Size(100f, 100f), Size(200f, 100f))
        // Рамка 60×60 с центром (80, 50) выходит за правый край холста до x = 110.
        val frame = checkNotNull(
            buildItemFrame(
                border = Border(80f, 50f, 60f, 60f),
                mode = FramingMode.FREE,
                content = FrameContent(
                    image = photo,
                    srcW = 100,
                    srcH = 100,
                    avgColor = 0xFFFF0000.toInt(),
                    storedScale = 0.6f,
                    storedPanX = 0f,
                    storedPanY = 0f,
                ),
            )
        )

        val image = rasterize(200, 100) {
            drawCombo(
                t = transform,
                canvasW = 100f,
                canvasH = 100f,
                background = ResolvedBackground.Solid(0xFF000000.toInt()),
                frames = listOf(frame),
            )
        }

        // Внутри холста рамка нарисована: канвасная (70,50) — экранная (120,50).
        assertEquals("рамка должна рисоваться внутри холста", 0xFF, alphaOf(image.pixel(120, 50)))
        // Свисающая часть легла бы здесь: экранная (155,50) — за правым краем холста (150).
        assertEquals("за пределами холста не должно быть пикселей рамки", 0, alphaOf(image.pixel(155, 50)))
        // Левое поле поверхности — вне холста: прозрачное, фон туда не заливается.
        assertEquals("вне холста фон не заливается", 0, alphaOf(image.pixel(40, 50)))
    }
}
