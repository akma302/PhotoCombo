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
import com.combo.core.image.ImageCodec
import com.combo.domain.PhotoRepository
import com.combo.ui.common.BACK_HOST_TAG
import com.combo.ui.common.BackDispatcher
import com.combo.ui.common.BackKeyHost
import com.combo.ui.common.LocalBackDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Изолированный Koin для UI-тестов: вне глобального контекста, чтобы тесты не делили
 * состояние. Экранам из него нужен только `ThumbImage` (репозиторий, декодер и
 * IO-диспетчер); ViewModel'и тесты передают в экраны явно.
 */
fun uiTestKoin(photoRepo: PhotoRepository): KoinApplication = koinApplication {
    modules(
        module {
            single<PhotoRepository> { photoRepo }
            single<ImageCodec> { StubImageCodec() }
            single<CoroutineDispatcher> { Dispatchers.IO }
        },
    )
}

/**
 * Поднимает экран в UI-тесте так же, как AppNav в приложении: изолированный Koin,
 * диспетчер «назад» в CompositionLocal и хост, который ловит Esc и боковую кнопку мыши.
 * Тест шлёт Esc через [pressEsc] — клавиатурный эквивалент системной кнопки «назад».
 */
@Composable
fun TestScreen(
    koin: KoinApplication,
    backDispatcher: BackDispatcher,
    content: @Composable () -> Unit,
) {
    KoinIsolatedContext(koin) {
        CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
            BackKeyHost(backDispatcher) { content() }
        }
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
