package com.combo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

private class StackViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * Хранилище ViewModel, живущее ровно столько, сколько экран в стеке навигации.
 *
 * Нужно потому, что Nav3 на desktop не даёт каждой записи своего `ViewModelStoreOwner`:
 * без него `koinViewModel` вернул бы один и тот же экземпляр всем записям одного класса, и
 * билдер для второго комбо открылся бы поверх состояния первого. `clear()` при уходе с
 * экрана отменяет `viewModelScope` — как это делал Nav2 на Android.
 *
 * В `DisposableEffect`, а не в `LaunchedEffect`: очистка обязана произойти и когда
 * композиция разбирается целиком (выход из приложения), — эффект на `onDispose` это
 * гарантирует.
 */
@Composable
fun rememberEntryViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember { StackViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}

/**
 * Хранилище корневых вкладок: переживает любые переходы внутри вкладки, очищается только
 * вместе с приложением. Вкладка Libraries и вкладка Combos должны сохранять выбранное и
 * прокрученное при открытии просмотра и возврате назад.
 */
@Composable
fun rememberRootViewModelStoreOwner(): ViewModelStoreOwner = remember { StackViewModelStoreOwner() }
