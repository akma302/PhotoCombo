package com.combo.feature.builder

import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.ItemSnap
import com.combo.feature.builder.draft.trimmedToFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Внешние границы композиции не зависят от вида редактора и пропорций рабочего холста. */
class FinalizeCanvasTest {
    private fun frame(id: String, x: Float, y: Float, w: Float, h: Float) = ItemSnap(
        id = id, zIndex = 0, centerX = x, centerY = y, width = w, height = h,
        rotation = 0f, framingMode = FramingMode.FREE, photoId = "p-$id",
        contentScale = 0.5f, panX = 11f, panY = -7f,
    )

    private fun canvas(vararg frames: ItemSnap) = ComboSnap(
        canvasW = 1600f, canvasH = 900f, background = BackgroundSpec.Auto(), items = frames.toList(),
    )

    @Test
    fun `a single frame determines both dimensions without changing its contents`() {
        val item = frame("one", 700f, 350f, 400f, 250f)
        val result = canvas(item).trimmedToFrames()

        assertEquals(400f, result.canvasW, 0f)
        assertEquals(250f, result.canvasH, 0f)
        assertEquals(item.copy(centerX = 200f, centerY = 125f), result.items.single())
    }

    @Test
    fun `horizontal bounds follow extreme frames and height follows the tallest centred frame`() {
        val result = canvas(
            frame("left", 200f, 400f, 200f, 150f),
            frame("middle", 500f, 400f, 400f, 300f),
            frame("right", 800f, 400f, 200f, 600f),
        ).trimmedToFrames()

        assertEquals(800f, result.canvasW, 0f)
        assertEquals(600f, result.canvasH, 0f)
        assertEquals(listOf(100f, 400f, 700f), result.items.map { it.centerX })
        assertEquals(listOf(300f, 300f, 300f), result.items.map { it.centerY })
    }

    @Test
    fun `vertically offset and overlapping frames are included in full`() {
        val result = canvas(
            frame("lower", 300f, 550f, 400f, 300f),
            frame("upper", 450f, 150f, 200f, 200f),
        ).trimmedToFrames()

        assertEquals(450f, result.canvasW, 0f)
        assertEquals(650f, result.canvasH, 0f)
        assertEquals(listOf("lower", "upper"), result.items.map { it.id })
        assertEquals(listOf(500f, 100f), result.items.map { it.centerY })
    }

    @Test
    fun `frames extending beyond the working canvas are retained`() {
        val result = canvas(
            frame("left", 0f, 0f, 200f, 100f),
            frame("right", 1600f, 900f, 200f, 100f),
        ).trimmedToFrames()

        assertEquals(1800f, result.canvasW, 0f)
        assertEquals(1000f, result.canvasH, 0f)
        assertEquals(100f, result.items.first().centerX, 0f)
        assertEquals(50f, result.items.first().centerY, 0f)
    }

    @Test
    fun `fractional bounds round outwards and repeated finalization is stable`() {
        val original = canvas(frame("one", 300.25f, 250.5f, 100.25f, 200.5f))
        val result = original.trimmedToFrames()

        assertEquals(101f, result.canvasW, 0f)
        assertEquals(201f, result.canvasH, 0f)
        val item = result.items.single()
        assertEquals(0f, item.centerX - item.width / 2f, 0f)
        assertEquals(0f, item.centerY - item.height / 2f, 0f)
        assertTrue(item.centerX + item.width / 2f <= result.canvasW)
        assertTrue(item.centerY + item.height / 2f <= result.canvasH)
        assertEquals(result, result.trimmedToFrames())
        assertEquals(1600f, original.canvasW, 0f)
        assertEquals(300.25f, original.items.single().centerX, 0f)
    }

    @Test
    fun `a canvas already filled by a frame stays unchanged`() {
        val original = canvas(frame("one", 800f, 450f, 1600f, 900f))
        assertEquals(original, original.trimmedToFrames())
    }

    @Test
    fun `an empty draft is left for the save validation`() {
        val original = canvas()
        assertEquals(original, original.trimmedToFrames())
    }
}
