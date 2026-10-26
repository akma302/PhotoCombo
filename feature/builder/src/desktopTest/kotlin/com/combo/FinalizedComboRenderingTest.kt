package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.combo.core.background.ResolvedBackground
import com.combo.core.model.FramingMode
import com.combo.core.render.Border
import com.combo.core.render.CanvasTransform
import com.combo.core.render.FrameContent
import com.combo.core.render.buildItemFrame
import com.combo.core.render.drawCombo
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.ItemSnap
import com.combo.feature.builder.draft.trimmedToFrames
import com.combo.core.background.BackgroundSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** Финальный холст сохраняет пиксели фото, полей рамок и промежутков между рамками. */
class FinalizedComboRenderingTest {
    private fun frame(id: String, x: Float, y: Float, w: Float, h: Float, mode: FramingMode) = ItemSnap(
        id = id, zIndex = 0, centerX = x, centerY = y, width = w, height = h,
        rotation = 0f, framingMode = mode, photoId = "p1",
        contentScale = 3f, panX = 2f, panY = -1f,
    )

    private fun render(snap: ComboSnap): ImageBitmap {
        val image = ImageBitmap(snap.canvasW.toInt(), snap.canvasH.toInt())
        val photo = ImageFixtures.patternBitmap()
        val frames = snap.items.map { item ->
            checkNotNull(buildItemFrame(
                border = Border(item.centerX, item.centerY, item.width, item.height),
                mode = item.framingMode,
                content = FrameContent(
                    image = photo, srcW = photo.width, srcH = photo.height,
                    avgColor = 0xFF808080.toInt(), storedScale = item.contentScale,
                    storedPanX = item.panX, storedPanY = item.panY,
                ),
            ))
        }
        CanvasDrawScope().draw(
            density = Density(1f), layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image), size = Size(snap.canvasW, snap.canvasH),
        ) {
            drawCombo(
                t = CanvasTransform(1f, Offset.Zero),
                canvasW = snap.canvasW, canvasH = snap.canvasH,
                background = ResolvedBackground.Solid(0xFF123456.toInt()), frames = frames,
            )
        }
        return image
    }

    @Test
    fun `finalized image equals the frame bounding region of the working canvas`() {
        val original = ComboSnap(
            canvasW = 96f, canvasH = 64f, background = BackgroundSpec.Solid(0xFF123456.toInt()),
            items = listOf(
                frame("free", 18f, 26f, 20f, 20f, FramingMode.FREE),
                frame("fit", 42f, 30f, 24f, 36f, FramingMode.FIT),
            ),
        )
        val finalized = original.trimmedToFrames()
        val before = render(original).asSkiaBitmap()
        val after = render(finalized).asSkiaBitmap()

        assertEquals(46, after.width)
        assertEquals(36, after.height)
        for (y in 0 until after.height) {
            for (x in 0 until after.width) {
                assertEquals("пиксель ($x, $y)", before.getColor(x + 8, y + 12), after.getColor(x, y))
            }
        }
    }
}
