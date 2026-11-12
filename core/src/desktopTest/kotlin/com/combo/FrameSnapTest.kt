package com.combo

import com.combo.core.render.Border
import com.combo.core.render.snap.FrameSnap
import com.combo.core.render.snap.FrameSnapState
import com.combo.core.render.snap.SnapAxis
import com.combo.core.render.snap.SnapDistances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSnapTest {
    private val distances = SnapDistances(capture = 5f, release = 8f, preview = 12f)
    private val target = Border(240f, 340f, 120f, 140f)

    @Test
    fun `pairs each edge with either target edge and aligns centers on both axes`() {
        for ((center, guide) in listOf(210f to 180f, 270f to 300f, 330f to 300f, 150f to 180f, 240f to 240f)) {
            val frame = Border(center - 14f, 585f, 60f, 60f)
            val step = FrameSnap(1000f, 800f, frame, listOf(target))
                .move(FrameSnapState(frame), 11f, 0f, distances)
            assertEquals(frame.copy(centerX = center), step.placed)
            assertEquals(SnapAxis.X, step.guides.single().axis)
            assertEquals(guide, step.guides.single().coordinate, 0f)
        }
        for ((center, guide) in listOf(300f to 270f, 380f to 410f, 440f to 410f, 240f to 270f, 340f to 340f)) {
            val frame = Border(685f, center - 14f, 60f, 60f)
            val step = FrameSnap(1000f, 800f, frame, listOf(target))
                .move(FrameSnapState(frame), 0f, 11f, distances)
            assertEquals(frame.copy(centerY = center), step.placed)
            assertEquals(SnapAxis.Y, step.guides.single().axis)
            assertEquals(guide, step.guides.single().coordinate, 0f)
        }
    }

    @Test
    fun `snaps at each canvas corner independently of neighboring frames`() {
        for (left in listOf(true, false)) for (top in listOf(true, false)) {
            val x = if (left) 30f else 970f
            val y = if (top) 20f else 780f
            val dx = if (left) -11f else 11f
            val dy = if (top) -11f else 11f
            val frame = Border(x - dx * 1.25f, y - dy * 1.25f, 60f, 40f)
            val step = FrameSnap(1000f, 800f, frame, emptyList())
                .move(FrameSnapState(frame), dx, dy, distances)
            assertEquals(Border(x, y, 60f, 40f), step.placed)
            assertEquals(listOf(SnapAxis.X, SnapAxis.Y), step.guides.map { it.axis })
            assertEquals(listOf(if (left) 0f else 1000f, if (top) 0f else 800f), step.guides.map { it.coordinate })
        }
    }

    @Test
    fun `holds a guide and breaks away forwards and backwards during one drag`() {
        val frame = Border(220f, 585f, 60f, 60f)
        val engine = FrameSnap(1000f, 800f, frame, listOf(target))
        var state = FrameSnapState(frame)
        fun move(dx: Float, expectedFree: Float, expectedPlaced: Float, captured: Boolean) {
            val step = engine.move(state, dx, 0f, distances)
            state = step.state
            assertEquals(expectedFree, state.free.centerX, 0f)
            assertEquals(expectedPlaced, step.placed.centerX, 0f)
            assertEquals(captured, step.guides.isNotEmpty())
        }
        move(17f, 237f, 240f, true)
        move(9f, 246f, 240f, true)
        move(4f, 250f, 250f, false)
        move(-7f, 243f, 240f, true)
        move(-12f, 231f, 231f, false)
    }

    @Test
    fun `preview changes guides only and bypass restores free position immediately`() {
        val frame = Border(220f, 585f, 60f, 60f)
        val engine = FrameSnap(1000f, 800f, frame, listOf(target))
        val state = FrameSnapState(frame)
        val off = engine.move(state, 10f, 0f, distances)
        val on = engine.move(state, 10f, 0f, distances, preview = true)
        assertEquals(off.placed, on.placed)
        assertTrue(off.guides.isEmpty())
        assertTrue(on.guides.single().preview)

        val snapped = engine.move(on.state, 6f, 0f, distances, preview = true)
        assertFalse(snapped.guides.single().preview)
        val bypass = snapped.state.withoutSnap()
        assertEquals(236f, bypass.placed.centerX, 0f)
        assertTrue(bypass.guides.isEmpty())
        assertTrue(engine.move(bypass.state, 0f, 0f, distances, preview = true).guides.isEmpty())
        assertEquals(237f, engine.move(bypass.state, 1f, 0f, distances, bypass = true).placed.centerX, 0f)
    }

    @Test
    fun `can move away from an already aligned frame and preserves canvas bounds`() {
        val frame = Border(target.centerX, 585f, 60f, 60f)
        val engine = FrameSnap(1000f, 800f, frame, listOf(target))
        val movingAway = engine.move(FrameSnapState(frame), 3f, 0f, distances, preview = true)
        assertEquals(243f, movingAway.placed.centerX, 0f)
        assertTrue(movingAway.guides.isEmpty())

        val oversized = Border(20f, 20f, 2100f, 1700f)
        val bounded = FrameSnap(1000f, 800f, oversized, emptyList())
            .move(FrameSnapState(oversized), -100f, -100f, distances)
        assertEquals(oversized.copy(centerX = 0f, centerY = 0f), bounded.placed)
        assertTrue(bounded.guides.isEmpty())
    }

    @Test
    fun `uses the nearer frame on ties and keeps a captured guide when another is closer`() {
        val frame = Border(190f, 585f, 60f, 60f)
        val engine = FrameSnap(1000f, 800f, frame, listOf(
            Border(200f, 180f, 120f, 140f), Border(208f, 600f, 120f, 140f),
        ))
        val caught = engine.move(FrameSnapState(frame), 14f, 0f, distances)
        assertEquals(208f, caught.placed.centerX, 0f)
        val held = engine.move(caught.state, -4f, 0f, distances)
        assertEquals(208f, held.placed.centerX, 0f)
        assertEquals(200f, held.state.free.centerX, 0f)
    }
}
