package com.combo.feature.builder

import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.core.render.ItemDraft
import com.combo.data.PhotoEntity
import com.combo.ui.common.PhotoGrid

/**
 * Окно «+Photo»: выбор фото, ещё не лежащих в выбранной рамке.
 *
 * Список кандидатов, проверку лимита и решение «пора закрыть окно» берёт на себя
 * вызывающий ([BuilderToolbar]). `item.contents` — МУТАБЕЛЬНЫЙ драфт, и после отправки
 * [BuilderEvent.AddPhotos] он уже содержит добавленное фото, поэтому лимит проверяется
 * ПОСЛЕ отправки события, а не до неё (до отправки счёт был бы занижен на единицу).
 *
 * Раскладка: три колонки и минимальный зазор — значения [PhotoGrid] по умолчанию.
 *
 * @param item рамка, в которую добавляем. Передаётся та же ссылка на драфт, что лежит в
 *   состоянии: копия не отражала бы добавленное фото при проверке лимита.
 * @param onPick выбранное фото. Отправляется [BuilderEvent.AddPhotos] на ОДНО фото;
 *   следующие добавляются повторными нажатиями (мультивыбора в окне нет).
 */
@Composable
internal fun AddPhotoDialog(
    item: ItemDraft,
    candidates: List<PhotoEntity>,
    onPick: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Add photos to item") },
        text = {
            PhotoGrid(
                photos = candidates,
                modifier = Modifier.height(320.dp),
                cellTag = { addPhotoCellTag(it.id) },
                cellPadding = 4.dp,
                cellHeight = 90.dp,
                onCellClick = onPick,
            )
        },
    )
}