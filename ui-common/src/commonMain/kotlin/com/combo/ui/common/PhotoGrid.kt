package com.combo.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.data.PhotoEntity

/** Адаптер общей галереи: квадратные фото, выделение и desktop-жесты библиотеки. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    photos: List<PhotoEntity>,
    onCellClick: (PhotoEntity) -> Unit,
    cellTag: (PhotoEntity) -> String,
    modifier: Modifier = Modifier,
    referenceItemWidth: Dp = 240.dp,
    state: LazyGridState = rememberLazyGridState(),
    verticalGap: Dp = Dp.Hairline,
    horizontalGap: Dp = Dp.Hairline,
    cellPadding: Dp = 0.dp,
    cellHeight: Dp? = null,
    isSelected: (PhotoEntity) -> Boolean = { false },
    onCellLongClick: ((PhotoEntity) -> Unit)? = null,
    thumbnail: @Composable (PhotoEntity, Modifier) -> Unit = { photo, imageModifier ->
        ThumbImage(photo.id, imageModifier)
    },
) {
    // Обработчик может смениться при рекомпозиции родителя, а pointerInput нет: без
    // обёртки замыкание внутри осталось бы старым.
    val longClickState = rememberUpdatedState(onCellLongClick)
    GalleryGrid(
        items = photos,
        key = { it.id },
        referenceItemWidth = referenceItemWidth,
        state = state,
        modifier = modifier,
        verticalGap = verticalGap,
        horizontalGap = horizontalGap,
    ) { photo, _ ->
        val selected = isSelected(photo)
        // Отступ ставится ДО обработчика жеста, поэтому нажатие по полям ячейки не
        // срабатывает: клик «висит» на самой миниатюре, а не на обёртке.
        val padded = if (cellPadding > 0.dp) Modifier.padding(cellPadding) else Modifier
        // combinedClickable подключается только там, где долгое нажатие нужно (в
        // библиотеке оно включает мультивыбор). Иначе в диалоге появился бы детектор
        // долгого нажатия, который ничего не делает, но меняет разбор тапа.
        val gesture = if (onCellLongClick == null) {
            Modifier.clickable { onCellClick(photo) }
        } else {
            Modifier.combinedClickable(
                onClick = { onCellClick(photo) },
                onLongClick = { onCellLongClick(photo) },
            )
        }
        // Долгое нажатие на desktop заменяют правый клик и Ctrl+клик: мышь не
        // «держат» так, как палец. Событие гасится в Initial-фазе вместе с
        // отпусканием, иначе clickable увидел бы в правом клике обычный клик.
        val secondaryClick = if (onCellLongClick == null) {
            Modifier
        } else {
            Modifier.pointerInput(photo.id) {
                var swallowing = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press &&
                            (
                                event.buttons.isSecondaryPressed ||
                                    (event.buttons.isPrimaryPressed && event.keyboardModifiers.isCtrlPressed)
                                )
                        ) {
                            swallowing = true
                            event.changes.forEach { it.consume() }
                            longClickState.value?.invoke(photo)
                        } else if (swallowing) {
                            event.changes.forEach { it.consume() }
                            if (event.type == PointerEventType.Release) swallowing = false
                        }
                    }
                }
            }
        }
        Box(
            modifier = padded
                .then(gesture)
                .then(secondaryClick)
                .testTag(cellTag(photo)),
        ) {
            // Заливка ячейки различается у экранов намеренно: библиотеке нужны
            // квадратные превью, диалогу — миниатюра заданной высоты.
            thumbnail(
                photo,
                if (cellHeight == null) {
                    Modifier.fillMaxSize().aspectRatio(1f)
                } else {
                    Modifier.fillMaxWidth().height(cellHeight)
                },
            )
            if (selected) {
                PcIcon(
                    PcIcons.Check,
                    contentDescription = "Selected",
                    tint = PcTheme.colors.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .border(3.dp, PcTheme.colors.accent),
                )
            }
        }
    }
}
