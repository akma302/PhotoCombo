package com.combo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Стек обработчиков «назад» ([BackDispatcher]) — замена `BackHandler` на desktop.
 *
 * Проверяется семантика LIFO и то, что обработчик снимается только явно (при закрытии
 * диалога), а не самим событием: диалог, который по Esc решил остаться открытым, обязан
 * получить и следующий Esc.
 */
class BackDispatcherTest {

    @Test
    fun `the last registered handler receives the event first`() {
        val dispatcher = BackDispatcher()
        val order = mutableListOf<String>()
        dispatcher.push { order += "нижний" }
        dispatcher.push { order += "верхний" }

        assertTrue(dispatcher.dispatchBack())

        assertEquals("верхний получает событие первым", listOf("верхний"), order)
    }

    @Test
    fun `an event with an empty stack reports that nobody handled it`() {
        val dispatcher = BackDispatcher()

        assertFalse("пустой стек — событие не обработано", dispatcher.dispatchBack())
    }

    /**
     * Событие не снимает обработчик: пока диалог открыт, каждый Esc снова попадает в
     * него. Снимается он только явно — через [BackDispatcher.remove] при закрытии.
     */
    @Test
    fun `dispatching does not pop the handler`() {
        val dispatcher = BackDispatcher()
        var calls = 0
        dispatcher.push { calls++ }

        dispatcher.dispatchBack()
        dispatcher.dispatchBack()

        assertEquals(2, calls)
        assertEquals("обработчик остаётся на стеке", 1, dispatcher.size)
    }

    @Test
    fun `a removed handler no longer receives events`() {
        val dispatcher = BackDispatcher()
        val order = mutableListOf<String>()
        dispatcher.push { order += "нижний" }
        val top: () -> Unit = { order += "верхний" }
        dispatcher.push(top)

        dispatcher.remove(top)
        assertTrue(dispatcher.dispatchBack())

        assertEquals(listOf("нижний"), order)
        assertEquals("снят только верхний", 1, dispatcher.size)
    }

    @Test
    fun `size counts the registered handlers`() {
        val dispatcher = BackDispatcher()
        val first: () -> Unit = {}
        val second: () -> Unit = {}

        dispatcher.push(first)
        dispatcher.push(second)
        assertEquals(2, dispatcher.size)

        dispatcher.remove(first)
        assertEquals(1, dispatcher.size)
    }
}
