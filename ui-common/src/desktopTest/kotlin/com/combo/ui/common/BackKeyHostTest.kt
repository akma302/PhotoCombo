package com.combo.ui.common

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Хост «назад» ([BackKeyHost]): Esc уходит в [BackDispatcher] и когда фокус на самом
 * хосте, и когда он на кнопке внутри экрана.
 *
 * Хост — тестируемый путь Esc на desktop; в приложении то же событие ловит обработчик
 * уровня окна, а хост отвечает за боковую кнопку мыши.
 */
@OptIn(ExperimentalTestApi::class)
class BackKeyHostTest {

    @Test
    fun `escape reaches the dispatcher through the host`() = runComposeUiTest {
        val dispatcher = BackDispatcher()
        var calls = 0
        dispatcher.push { calls++ }
        setContent {
            BackKeyHost(dispatcher) { Text("probe") }
        }
        waitForIdle()

        onNodeWithTag(BACK_HOST_TAG).requestFocus().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(1, calls)
    }

    /**
     * Preview-фаза идёт от корня вниз, поэтому Esc перехватывается хостом раньше, чем
     * кнопка под фокусом успевает что-то с ним сделать.
     */
    @Test
    fun `escape is handled while focus sits on a child button`() = runComposeUiTest {
        val dispatcher = BackDispatcher()
        var calls = 0
        dispatcher.push { calls++ }
        setContent {
            BackKeyHost(dispatcher) {
                TextButton(onClick = {}, modifier = Modifier.testTag("probe_button")) {
                    Text("button")
                }
            }
        }
        waitForIdle()

        onNodeWithTag("probe_button").performClick()
        waitForIdle()
        onNodeWithTag(BACK_HOST_TAG).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(1, calls)
    }
}
