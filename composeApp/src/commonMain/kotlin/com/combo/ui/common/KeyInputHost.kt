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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Фокусируемый хост клавиатурных сокращений экрана: [onKeyDown] вызывается в
 * preview-фазе для каждого нажатия, пока узел в композиции, и может его погасить
 * (`true` — обработано).
 *
 * Фокус запрашивается один раз при появлении: preview-фаза идёт от корня вниз, поэтому
 * сокращения экрана работают, пока фокус внутри хоста — даже если он на поле ввода или
 * кнопке. Хост не перехватывает указатель: клавиши приходят независимо от мыши.
 */
@Composable
fun KeyInputHost(
    onKeyDown: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && onKeyDown(event)
            },
    ) {
        content()
    }
}
