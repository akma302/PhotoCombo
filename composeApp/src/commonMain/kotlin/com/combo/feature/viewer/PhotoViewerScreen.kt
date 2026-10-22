package com.combo.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.KeyInputHost
import kotlinx.coroutines.launch
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

/** Оформление и ввод просмотра отделены от загрузки для воспроизводимых UI-проверок. */
@Composable
internal fun PhotoViewerContent(
    photoId: String,
    state: PhotoViewerViewModel.State,
    onBack: () -> Unit,
    ensureLoaded: (Int) -> Unit,
) {
    if (state.photos.isEmpty()) {
        ViewerChrome("", onBack) { Box(Modifier.fillMaxSize()) }
        return
    }
    // Начальная страница задаётся до первого измерения и загрузки соседей.
    val startIndex = state.photos.indexOfFirst { it.id == photoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex) { state.photos.size }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }
    LaunchedEffect(pagerState.currentPage, state.photos.size) {
        ensureLoaded(pagerState.currentPage)
    }

    val scope = rememberCoroutineScope()

    ViewerChrome(state.photos.getOrNull(pagerState.currentPage)?.originalName.orEmpty(), onBack) {
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
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val photo = state.photos.getOrNull(page)
                    val image = photo?.let { state.images[it.id] }
                    Box(
                        Modifier.fillMaxSize().background(PcTheme.colors.workspace),
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

/** Название не вытесняет кнопку возврата даже при длинном имени и крупном шрифте. */
@Composable
private fun ViewerChrome(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    PcSurface(Modifier.fillMaxSize().testTag("photo_viewer"), color = PcTheme.colors.workspace) {
        Column(Modifier.fillMaxSize()) {
            PcSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(PcTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    PcIconButton(PcIcons.Back, "Back", onBack)
                    PcText(title, Modifier.weight(1f).padding(horizontal = PcTheme.spacing.sm),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            PcDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
