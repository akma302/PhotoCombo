package com.combo

import com.combo.core.render.Border
import com.combo.core.render.Edge
import com.combo.core.render.snap.FrameSnap
import com.combo.core.render.snap.FrameSnapState
import com.combo.core.render.snap.SnapAxis
import com.combo.core.render.snap.SnapDistances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSnapTest {
    private val distances = SnapDistances(capture = 5f, release = 8f)
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
    fun `bypass restores free position immediately and guides appear only on capture`() {
        val frame = Border(220f, 585f, 60f, 60f)
        val engine = FrameSnap(1000f, 800f, frame, listOf(target))
        val near = engine.move(FrameSnapState(frame), 10f, 0f, distances)
        assertTrue(near.guides.isEmpty())

        val snapped = engine.move(near.state, 6f, 0f, distances)
        assertEquals(240f, snapped.guides.single().coordinate, 0f)
        val bypass = snapped.state.withoutSnap()
        assertEquals(236f, bypass.placed.centerX, 0f)
        assertTrue(bypass.guides.isEmpty())
        assertTrue(engine.move(bypass.state, 0f, 0f, distances).guides.isEmpty())
        assertEquals(237f, engine.move(bypass.state, 1f, 0f, distances, bypass = true).placed.centerX, 0f)
    }

    @Test
    fun `can move away from an already aligned frame and preserves canvas bounds`() {
        val frame = Border(target.centerX, 585f, 60f, 60f)
        val engine = FrameSnap(1000f, 800f, frame, listOf(target))
        val movingAway = engine.move(FrameSnapState(frame), 3f, 0f, distances)
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

    @Test
    fun `photo snaps matching edges on both axes at any relative size`() {
        val frame = Border(300f, 300f, 200f, 160f)
        for (sizeDifference in listOf(60f, -60f)) {
            for (edgeDirection in listOf(1f, -1f)) {
                val center = 300f + edgeDirection * sizeDifference / 2f
                val aligned = Border(center, center, frame.width + sizeDifference, frame.height + sizeDifference)
                val photo = aligned.copy(centerX = center - 15f, centerY = center - 15f)
                val snap = FrameSnap.photo(frame, photo)
                val held = snap.move(FrameSnapState(photo), 12f, 12f, distances)
                assertEquals(aligned, held.placed)
                assertEquals(
                    listOf(300f - edgeDirection * 100f, 300f - edgeDirection * 80f),
                    held.guides.map { it.coordinate },
                )
                val xGuide = held.guides.first()
                assertEquals(220f, xGuide.start, 0f)
                assertEquals(380f, xGuide.end, 0f)

                val releasedX = snap.move(held.state, 12f, 0f, distances)
                assertEquals(aligned.copy(centerX = center + 9f), releasedX.placed)
                assertEquals(SnapAxis.Y, releasedX.guides.single().axis)
            }
        }
    }

    @Test
    fun `each dragged frame edge snaps to its canvas boundary while keeping its anchor`() {
        val frame = Border(300f, 300f, 200f, 200f)
        for (edge in Edge.entries) {
            val horizontal = edge == Edge.LEFT || edge == Edge.RIGHT
            val direction = if (edge == Edge.LEFT || edge == Edge.TOP) -1f else 1f
            val boundary = if (direction < 0f) 0f else 600f
            val delta = direction * 197f
            for (oneSided in listOf(true, false)) {
                val snap = FrameSnap.resize(600f, 600f, frame, emptyList(), edge, 50f, oneSided)
                val held = snap.move(
                    FrameSnapState(frame), if (horizontal) delta else 0f, if (horizontal) 0f else delta, distances,
                )
                assertEquals(boundary, held.guides.single().coordinate, 0f)
                val center = if (oneSided) 300f + direction * 100f else 300f
                val size = if (oneSided) 400f else 600f
                val expected = if (horizontal) frame.copy(centerX = center, width = size)
                else frame.copy(centerY = center, height = size)
                assertEquals(expected, held.placed)
            }
        }
    }

    @Test
    fun `resize cannot snap past minimum size and bypass keeps the fixed edge`() {
        val frame = Border(400f, 300f, 200f, 160f)
        val nearby = Border(550f, 200f, 20f, 40f)
        val tooClose = Border(330f, 200f, 38f, 40f)
        val snap = FrameSnap.resize(800f, 600f, frame, listOf(nearby, tooClose), Edge.RIGHT, 50f, oneSided = true)
        val held = snap.move(FrameSnapState(frame), 37f, 0f, distances)
        assertEquals(540f, held.guides.single().coordinate, 0f)
        assertEquals(frame.copy(centerX = 420f, width = 240f), held.placed)
        val bypass = held.state.withoutSnap()
        assertEquals(frame.copy(centerX = 418.5f, width = 237f), bypass.placed)
        val minimum = snap.move(bypass.state, -500f, 0f, distances)
        assertEquals(frame.copy(centerX = 325f, width = 50f), minimum.placed)
        assertTrue(minimum.guides.isEmpty())
    }
}
