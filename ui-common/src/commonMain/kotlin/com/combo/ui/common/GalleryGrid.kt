package com.combo.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/** Ширина уже исключает padding; в пикселях используется тот же расчёт, что при измерении grid. */
fun galleryColumnCount(availableWidth: Int, referenceWidth: Int, gap: Int): Int {
    require(referenceWidth > 0 && gap >= 0)
    return floor((availableWidth.coerceAtLeast(0).toDouble() + gap) / (referenceWidth.toDouble() + gap))
        .toInt().coerceAtLeast(1)
}

data class GalleryCellGeometry(val width: Dp, val previewHeight: Dp)

/** Общая сетка владеет размещением; данные, ввод и отрисовка остаются у вызывающего. */
@Composable
fun <T> GalleryGrid(
    items: List<T>,
    key: (T) -> Any,
    referenceItemWidth: Dp,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalGap: Dp = 0.dp,
    verticalGap: Dp = horizontalGap,
    previewAspectRatio: (T) -> Float = { 1f },
    itemContent: @Composable (T, GalleryCellGeometry) -> Unit,
) {
    val density = LocalDensity.current
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val padding = contentPadding.calculateLeftPadding(direction) + contentPadding.calculateRightPadding(direction)
        val widthPx = with(density) { (maxWidth - padding).roundToPx().coerceAtLeast(0) }
        val gapPx = with(density) { horizontalGap.roundToPx() }
        val columns = galleryColumnCount(widthPx, with(density) { referenceItemWidth.roundToPx().coerceAtLeast(1) }, gapPx)
        val cellWidth = with(density) { ((widthPx - gapPx * (columns - 1)).coerceAtLeast(0) / columns).toDp() }
        val rowRatios = remember(items, columns, previewAspectRatio) {
            items.chunked(columns).map { row ->
                row.maxOf { item -> 1f / previewAspectRatio(item).also { require(it.isFinite() && it > 0f) } }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            verticalArrangement = Arrangement.spacedBy(verticalGap),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                itemContent(item, GalleryCellGeometry(cellWidth, cellWidth * rowRatios[index / columns]))
            }
        }
    }
}
