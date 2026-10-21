package com.combo.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.combo.ui.common.BackNavIcon
import com.combo.ui.common.KeyInputHost
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photoId: String,
    onBack: () -> Unit,
    vm: PhotoViewerViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    // Список фото приходит асинхронно (Flow из Room), поэтому при первой композиции
    // startIndex ещё 0, а rememberPagerState запоминает initialPage только один раз.
    // Чтобы открывалось нажатое фото, а не первое, один раз переходим на реальный
    // индекс, как только список загружен.
    val pagerState = rememberPagerState(initialPage = 0) { state.photos.size }
    val startIndex = remember(state.photos, photoId) {
        state.photos.indexOfFirst { it.id == photoId }.coerceAtLeast(0)
    }
    var initialPageApplied by remember(photoId) { mutableStateOf(false) }
    LaunchedEffect(startIndex, initialPageApplied) {
        if (!initialPageApplied && state.photos.isNotEmpty()) {
            pagerState.scrollToPage(startIndex)
            initialPageApplied = true
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }
    LaunchedEffect(pagerState.currentPage, state.photos.size) {
        vm.ensureLoaded(pagerState.currentPage)
    }

    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { BackNavIcon(onBack) },
            title = {
                val p = state.photos.getOrNull(pagerState.currentPage)
                Text(p?.originalName ?: "")
            },
        )
    }) { padding ->
        // Стрелки влево/вправо — клавиатурный эквивалент горизонтального свайпа:
        // листают страницы пейджера с теми же границами, что и свайп.
        KeyInputHost(onKeyDown = { event ->
            when (event.key) {
                Key.DirectionLeft -> {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                    true
                }
                Key.DirectionRight -> {
                    if (pagerState.currentPage < state.photos.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                    true
                }
                else -> false
            }
        }) {
            Box(Modifier.fillMaxSize().padding(padding).onSizeChanged { container = it }) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val photo = state.photos.getOrNull(page)
                    val image = photo?.let { state.images[it.id] }
                    Box(
                        Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null) {
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 8f)
                                            offset = if (scale > 1f) offset + pan else Offset.Zero
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
