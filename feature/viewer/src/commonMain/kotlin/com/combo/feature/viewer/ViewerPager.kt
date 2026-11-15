package com.combo.feature.viewer

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.combo.ui.common.KeyInputHost
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import kotlinx.coroutines.launch

@Composable
internal fun <T> ViewerPager(
    initialId: String,
    pages: List<T>,
    pageId: (T) -> String,
    title: (T) -> String,
    tag: String,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    emptyMessage: String? = null,
    content: @Composable (T, Modifier) -> Unit,
) {
    if (pages.isEmpty()) {
        ViewerChrome("", tag, onBack) {
            if (emptyMessage != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PcText(emptyMessage) }
            }
        }
        return
    }

    val startIndex = pages.indexOfFirst { pageId(it) == initialId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startIndex) { pages.size }
    val currentPage = pager.currentPage
    LaunchedEffect(currentPage) { onPageChanged(currentPage) }

    val scope = rememberCoroutineScope()
    KeyInputHost(onKeyDown = { event ->
        val delta = when (event.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            else -> 0
        }
        val target = pager.currentPage + delta
        if (delta != 0 && target in pages.indices) {
            scope.launch { pager.animateScrollToPage(target) }
        }
        delta != 0
    }) {
        ViewerChrome(pages.getOrNull(currentPage)?.let(title).orEmpty(), tag, onBack) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { pageId(pages[it]) },
            ) { page ->
                val item = pages[page]
                ZoomablePage(active = page == currentPage) { modifier -> content(item, modifier) }
            }
        }
    }
}

@Composable
private fun ZoomablePage(active: Boolean, content: @Composable (Modifier) -> Unit) {
    var scale by remember(active) { mutableStateOf(1f) }
    var offset by remember(active) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .transformable(transform, canPan = { scale > 1f }, enabled = active),
    ) {
        content(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}

@Composable
private fun ViewerChrome(title: String, tag: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    PcSurface(Modifier.fillMaxSize().testTag(tag), color = PcTheme.colors.workspace) {
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
