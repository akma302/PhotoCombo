package com.combo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.combo.di.appModule
import com.combo.di.desktopModule
import com.combo.di.viewModelModule
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.AppNav
import com.combo.ui.common.BackDispatcher
import org.koin.core.context.startKoin
import java.awt.Dimension

/**
 * Точка входа desktop-приложения: Koin, окно и навигация.
 *
 * Окно открывается размером 480×860 dp. Builder адаптирует инспектор к ширине окна;
 * минимальный размер 420×700 не позволяет сузить окно до нерабочего состояния.
 *
 * Esc обрабатывается на уровне окна, а не экранов: когда фокус в поле ввода или на
 * кнопке, событие иначе ушло бы в виджет. Дальше [BackDispatcher] сам решает, кто
 * получает «назад» — диалог, режим выбора или стек навигации.
 */
fun main() {
    startKoin {
        modules(appModule, desktopModule(), viewModelModule)
    }
    application {
        val backDispatcher = remember { BackDispatcher() }
        val windowState = rememberWindowState(width = 480.dp, height = 860.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "PhotoCombo",
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    backDispatcher.dispatchBack()
                    true
                } else {
                    false
                }
            },
        ) {
            window.minimumSize = Dimension(420, 700)
            MaterialTheme {
                PcDesktopTheme { AppNav(backDispatcher) }
            }
        }
    }
}
