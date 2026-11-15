package com.combo.ui.common

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/** The photo grids use photo IDs as keys. An empty set releases the grid's thumbnails. */
@Composable
fun ReportVisiblePhotos(state: LazyGridState, onVisiblePhotos: (Set<String>) -> Unit) {
    LaunchedEffect(state, onVisiblePhotos) {
        snapshotFlow {
            state.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
        }.collect(onVisiblePhotos)
    }
    DisposableEffect(state, onVisiblePhotos) {
        onDispose { onVisiblePhotos(emptySet()) }
    }
}
