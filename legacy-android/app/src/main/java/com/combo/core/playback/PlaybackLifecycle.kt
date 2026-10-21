package com.combo.core.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Связывает воспроизведение с жизненным циклом экрана: [onStop] вызывается, когда экран
 * уходит из видимости (таймеры нужно поставить на паузу), [onStart] — когда возвращается.
 */
@Composable
fun BindPlaybackLifecycle(onStop: () -> Unit, onStart: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onStop()
                Lifecycle.Event.ON_START -> onStart()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
