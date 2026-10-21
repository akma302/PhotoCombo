package com.combo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Стек обработчиков «назад» — LIFO, как системный back: последний зарегистрированный
 * (диалог, режим выбора) получает событие первым.
 *
 * Вместо `BackHandler` из activity-compose: на desktop его нет, а семантика нужна та же —
 * Esc и боковая кнопка мыши должны закрывать верхний диалог, а не уводить с экрана.
 * Хранилище — `mutableStateListOf`: регистрация обработчика обязана замечаться в
 * композиции, иначе повторный Esc не найдёт только что открытый диалог.
 */
class BackDispatcher {
    private val handlers = mutableStateListOf<() -> Unit>()

    /** Для тестов и отладки: сколько обработчиков сейчас на стеке. */
    val size: Int get() = handlers.size

    fun push(handler: () -> Unit) {
        handlers.add(handler)
    }

    fun remove(handler: () -> Unit) {
        handlers.remove(handler)
    }

    /** `true` — обработчик нашёлся и выполнен; `false` — стек пуст. */
    fun dispatchBack(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

/** Доступ к диспетчеру из любого экрана; в тестах подменяется на свой экземпляр. */
val LocalBackDispatcher = staticCompositionLocalOf<BackDispatcher> {
    error("BackDispatcher не предоставлен: AppNav должен положить его в LocalBackDispatcher")
}

/**
 * Регистрирует [onBack] верхним обработчиком, пока [enabled] и компонент в композиции.
 *
 * `rememberUpdatedState` — не перестраховка: лямбда пересоздаётся на каждой рекомпозиции,
 * а обработчик, положенный в стек при входе, живёт до `onDispose`. Без обновления он
 * навсегда защёлкнул бы первую версию лямбды — со старым состоянием экрана.
 */
@Composable
fun RegisterBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current
    val current = rememberUpdatedState(onBack)
    DisposableEffect(dispatcher, enabled) {
        if (!enabled) {
            onDispose {}
        } else {
            val handler: () -> Unit = { current.value() }
            dispatcher.push(handler)
            onDispose { dispatcher.remove(handler) }
        }
    }
}
