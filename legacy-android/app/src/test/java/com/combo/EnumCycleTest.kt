package com.combo

import com.combo.core.model.SwitchMode
import com.combo.core.model.TransitionType
import com.combo.core.util.next
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [next]: следующее значение enum'а по кругу.
 *
 * Порядок объявления значений в `SwitchMode` и `TransitionType` — контракт кнопок тулбара,
 * а не деталь реализации: если кто-то переставит значения, кнопки начнут листать их в
 * другом порядке, и тест упадёт вместо тихой смены поведения.
 */
class EnumCycleTest {

    @Test
    fun `next follows declaration order and wraps around`() {
        assertEquals(SwitchMode.AUTO, SwitchMode.MANUAL.next())
        assertEquals(SwitchMode.MANUAL_AUTO, SwitchMode.AUTO.next())
        assertEquals(SwitchMode.MANUAL, SwitchMode.MANUAL_AUTO.next())

        assertEquals(TransitionType.FADE, TransitionType.NONE.next())
        assertEquals(TransitionType.SLIDE, TransitionType.FADE.next())
        assertEquals(TransitionType.ZOOM, TransitionType.SLIDE.next())
        assertEquals(TransitionType.WIPE, TransitionType.ZOOM.next())
        assertEquals(TransitionType.NONE, TransitionType.WIPE.next())
    }

    /** Полный обход возвращает исходное значение для каждого элемента. */
    @Test
    fun `next is a closed cycle for every enum value`() {
        for (m in SwitchMode.entries) {
            var cur = m
            repeat(SwitchMode.entries.size) { cur = cur.next() }
            assertEquals("SwitchMode: обход не замкнулся на $m", m, cur)
        }
        for (t in TransitionType.entries) {
            var cur = t
            repeat(TransitionType.entries.size) { cur = cur.next() }
            assertEquals("TransitionType: обход не замкнулся на $t", t, cur)
        }
    }
}
