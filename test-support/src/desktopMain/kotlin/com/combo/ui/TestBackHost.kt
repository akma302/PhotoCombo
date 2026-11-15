package com.combo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.combo.ui.common.BACK_HOST_TAG
import com.combo.ui.common.BackDispatcher
import com.combo.ui.common.BackKeyHost
import com.combo.ui.common.LocalBackDispatcher

/** Mounts a screen with the same Back dispatcher used in the application. */
@Composable
fun TestScreen(
    backDispatcher: BackDispatcher,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
        BackKeyHost(backDispatcher) { content() }
    }
}

/**
 * Esc в узел [BACK_HOST_TAG]: событие уходит в тот же [BackDispatcher], что и «назад».
 *
 * Фокус хосту возвращаем явно: клик по кнопке, которая после смены режима исчезает из
 * композиции (например «Select» в топбаре), оставляет сцену вообще без фокуса, и тогда
 * инъекция клавиши не находит адресата. В приложении этого не происходит: Esc ловит
 * обработчик уровня окна, а хост — только тестируемый путь и боковая кнопка мыши.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.pressEsc() {
    onNodeWithTag(BACK_HOST_TAG).requestFocus().performKeyInput { pressKey(Key.Escape) }
}
