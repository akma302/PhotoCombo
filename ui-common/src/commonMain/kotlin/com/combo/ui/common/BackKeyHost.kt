package com.combo.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag

/** testTag хоста «назад»: UI-тесты шлют Esc в этот узел вместо системной кнопки. */
const val BACK_HOST_TAG = "back_host"

/**
 * Хост системного «назад»: Esc и боковая кнопка мыши уходят в [BackDispatcher].
 *
 * Обработчик живёт в композиции, а не на уровне окна, чтобы быть проверяемым: UI-тест
 * поднимает экран через этот хост и шлёт Esc в [BACK_HOST_TAG]. Хост фокусируем и
 * запрашивает фокус сам: preview-фаза доставляет нажатия от корня вниз, поэтому Esc
 * доходит до хоста даже когда фокус стоит в поле имени или на кнопке.
 *
 * Боковая кнопка мыши приходит событием `Press` с флагом `isBackPressed`; событие
 * гасится в Initial-фазе, чтобы кнопка не сработала ещё и как обычный клик под курсором.
 */
@Composable
fun BackKeyHost(dispatcher: BackDispatcher, content: @Composable () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(BACK_HOST_TAG)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    dispatcher.dispatchBack()
                    true
                } else {
                    false
                }
            }
            .pointerInput(dispatcher) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isBackPressed) {
                            event.changes.forEach { it.consume() }
                            dispatcher.dispatchBack()
                        }
                    }
                }
            },
    ) {
        content()
    }
}
