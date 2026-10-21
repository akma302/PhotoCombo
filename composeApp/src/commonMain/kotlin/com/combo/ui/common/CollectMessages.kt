package com.combo.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/**
 * Показывает поток сообщений ViewModel в snackbar.
 *
 * Один `LaunchedEffect` на весь поток, а не запуск на каждое сообщение: `showSnackbar`
 * suspend'ит до закрытия, и при быстрых сообщениях параллельные показы терялись бы.
 *
 * Ключ `Unit`, а не сам поток: он принадлежит ViewModel и переживает перекомпозиции,
 * так что пересборка коллектора по ссылке только прервала бы уже показанное сообщение.
 * Жизненный цикл коллектора всё равно привязан к экрану — при уходе с экрана
 * `LaunchedEffect` отменяется сам.
 */
@Composable
fun CollectMessages(messages: Flow<String>, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(Unit) {
        messages.collect { snackbarHostState.showSnackbar(it) }
    }
}
