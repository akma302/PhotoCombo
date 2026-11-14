package com.combo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

private class StackViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Keeps a store at its call site and cancels its ViewModels when that site leaves composition. */
@Composable
internal fun rememberCompositionViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember { StackViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}
