package com.combo.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.combo.data.PhotoEntity

/**
 * Сетка фото на `LazyVerticalGrid` — общая для библиотеки и диалога «+Photo» в билдере.
 *
 * Компонент ничего не знает про ViewModel: список, признак выделения и обработчики приходят
 * аргументами. Иначе «переиспользуемая» сетка была бы привязана к одному экрану — ровно то,
 * из-за чего она и выносится.
 *
 * Значения по умолчанию совпадают с дефолтами `LazyVerticalGrid` (зазор — hairline): их
 * специально не «подтянуто» до 2.dp из библиотеки, иначе переезд в общий компонент тихо
 * изменил бы раскладку диалога. Всё, что у экранов различается, — параметры:
 *
 * - библиотека: `verticalArrangement`/`horizontalArrangement` = 2.dp, `cellHeight = null`
 *   (квадрат по aspectRatio), без `cellPadding`, оверлей выделения включён;
 * - диалог «+Photo»: `cellHeight = 90.dp` + `cellPadding = 4.dp`, оверлей не нужен,
 *   долгое нажатие не нужно.
 *
 * Ключ ячейки — всегда `photo.id`: без него LazyGrid переиспользовал бы слот по индексу, и
 * при обновлении кандидатов в диалоге картинка приезжала бы к чужому фото.
 *
 * На desktop долгое нажатие приходит не только удержанием, но и правым кликом или
 * Ctrl+кликом: и то и другое вызывает [onCellLongClick] и не превращается в обычный клик.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    photos: List<PhotoEntity>,
    onCellClick: (PhotoEntity) -> Unit,
    cellTag: (PhotoEntity) -> String,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Fixed(3),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Dp.Hairline),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Dp.Hairline),
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
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
    ) {
        items(photos, key = { it.id }) { photo ->
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
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .border(3.dp, MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
