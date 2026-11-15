package com.combo.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.ContentScale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PhotoViewerScreen(
    photoId: String,
    onBack: () -> Unit,
    vm: PhotoViewerViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    PhotoViewerContent(photoId, state, onBack, vm::ensureLoaded)
}

@Composable
internal fun PhotoViewerContent(
    photoId: String,
    state: PhotoViewerViewModel.State,
    onBack: () -> Unit,
    ensureLoaded: (Int) -> Unit,
) {
    ViewerPager(
        initialId = photoId,
        pages = state.photos,
        pageId = { it.id },
        title = { it.originalName.orEmpty() },
        tag = "photo_viewer",
        onBack = onBack,
        onPageChanged = ensureLoaded,
    ) { photo, modifier ->
        state.images[photo.id]?.let { image ->
            Image(image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
        }
    }
}
