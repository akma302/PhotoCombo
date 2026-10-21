package com.combo

import com.combo.core.model.Direction
import com.combo.core.render.Fade
import com.combo.core.render.Slide
import com.combo.core.model.TransitionType
import com.combo.core.render.Wipe
import com.combo.core.playback.AutoScheduler
import com.combo.core.playback.SwitchAnimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.combo.core.render.create

class TransitionsTest {

    @Test
    fun `fade crossfades monotonically`() {
        val midOut = Fade.outgoing(0.5f, Direction.UP)
        val midIn = Fade.incoming(0.5f, Direction.UP)
        assertEquals(0.5f, midOut.alpha, 0.001f)
        assertEquals(0.5f, midIn.alpha, 0.001f)
        assertEquals(1f, Fade.incoming(1f, Direction.UP).alpha, 0.001f)
        assertEquals(0f, Fade.incoming(0f, Direction.UP).alpha, 0.001f)
    }

    @Test
    fun `slide outgoing follows direction, incoming from opposite side`() {
        val dir = Direction.UP
        assertEquals(1f, Slide.incoming(0f, dir).offsetNorm.y, 0.001f)
        assertEquals(0f, Slide.incoming(1f, dir).offsetNorm.y, 0.001f)
        assertEquals(-1f, Slide.outgoing(1f, dir).offsetNorm.y, 0.001f)
        assertEquals(-1f, Slide.incoming(0f, Direction.DOWN).offsetNorm.y, 0.001f)
    }

    @Test
    fun `wipe grows with progress and finishes full`() {
        val mid = Wipe.incoming(0.5f, Direction.UP)
        assertEquals(0.5f, mid.wipe, 0.001f)
        assertEquals(1f, Wipe.incoming(1f, Direction.UP).wipe, 0.001f)
        assertTrue(Wipe.incoming(0.5f, Direction.LEFT).wipe in 0f..1f)
    }

    @Test
    fun `factory creates correct implementation`() {
        assertTrue(TransitionType.FADE.create() === Fade)
        assertTrue(TransitionType.SLIDE.create() === Slide)
    }

    @Test
    fun `switch animator updates current immediately and animates`() {
        var time = 0L
        val animator = SwitchAnimator(now = { time })
        assertEquals(0, animator.currentIndex("a"))
        animator.switch("a", 2, TransitionType.FADE, Direction.UP)
        assertEquals(2, animator.currentIndex("a"))
        val (active, p0) = animator.frame("a")!!
        assertEquals(0, active.from)
        assertEquals(2, active.to)
        assertEquals(0f, p0, 0.001f)
        time += 150
        val pMid = animator.frame("a")!!.second
        assertTrue(pMid in 0f..1f)
        time += 1000
        assertTrue(animator.finished().contains("a"))
        assertNull(animator.frame("a"))
    }

    @Test
    fun `switch to same index is no-op`() {
        val animator = SwitchAnimator()
        animator.switch("a", 0, TransitionType.FADE, Direction.UP)
        assertNull(animator.frame("a"))
        assertFalse(animator.hasActive())
    }

    @Test
    fun `auto scheduler pause preserves remaining interval`() {
        var time = 1000L
        val scheduler = AutoScheduler(now = { time })
        scheduler.schedule("i", 3000)
        time += 1000
        scheduler.pause()
        time += 10_000
        assertTrue(scheduler.due().isEmpty())
        scheduler.resume()
        time += 1999
        assertTrue(scheduler.due().isEmpty())
        time += 1
        assertEquals(listOf("i"), scheduler.due())
    }

    @Test
    fun `manual swipe reschedules auto timer`() {
        var time = 0L
        val scheduler = AutoScheduler(now = { time })
        scheduler.schedule("i", 3000)
        time += 2500
        scheduler.reschedule("i")
        time += 1000
        assertTrue(scheduler.due().isEmpty())
        time += 2000
        assertEquals(listOf("i"), scheduler.due())
    }

    /** Тикер плеера держится на hasRunning(): без запланированных слотов due() пуст сразу. */
    @Test
    fun `scheduler reports running slots before first due`() {
        var time = 0L
        val scheduler = AutoScheduler(now = { time })
        assertFalse(scheduler.hasRunning())
        scheduler.schedule("i", 3000)
        assertTrue("запланированный слот должен считаться активным", scheduler.hasRunning())
        assertTrue(scheduler.due().isEmpty())
        time += 3000
        assertEquals(listOf("i"), scheduler.due())
    }

    @Test
    fun `scheduler has no running slots while paused`() {
        var time = 0L
        val scheduler = AutoScheduler(now = { time })
        scheduler.schedule("i", 3000)
        scheduler.pause()
        assertFalse("на паузе тикер крутиться не должен", scheduler.hasRunning())
        scheduler.resume()
        assertTrue(scheduler.hasRunning())
        scheduler.cancel("i")
        assertFalse(scheduler.hasRunning())
    }

    /** Тикер спит до ближайшего таймера, а не просыпается каждые 16 мс. */
    @Test
    fun `next delay reports time to nearest slot`() {
        var time = 0L
        val scheduler = AutoScheduler(now = { time })
        assertNull("без слотов спать не на что", scheduler.nextDelayMs())

        scheduler.schedule("a", 3000)
        assertEquals(3000L, scheduler.nextDelayMs())

        time = 1000
        assertEquals(2000L, scheduler.nextDelayMs())

        time = 2900
        assertEquals(100L, scheduler.nextDelayMs())

        time = 3000
        assertEquals("пора", 0L, scheduler.nextDelayMs())

        // На паузе работающих слотов нет -> null.
        scheduler.pause()
        assertNull(scheduler.nextDelayMs())

        // resume продолжает с остатка: до него осталось 0 мс (слот только что сработал).
        scheduler.resume()
        assertEquals(0L, scheduler.nextDelayMs())
    }

    @Test
    fun `next delay takes the nearest of several slots`() {
        var time = 0L
        val scheduler = AutoScheduler(now = { time })
        scheduler.schedule("far", 3000)
        scheduler.schedule("near", 800)
        assertEquals(800L, scheduler.nextDelayMs())
        scheduler.cancel("near")
        assertEquals(3000L, scheduler.nextDelayMs())
    }
}
