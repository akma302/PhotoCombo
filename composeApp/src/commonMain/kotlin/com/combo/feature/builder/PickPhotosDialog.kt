package com.combo.feature.builder

import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.data.PhotoEntity
import com.combo.ui.common.PhotoGrid

/**
 * Окно выбора фото из библиотеки. Одно окно на два действия:
 *
 * - «+Frame» ([multi] = `true`): фото отмечаются касанием, кнопка подтверждения создаёт по
 *   рамке на каждое отмеченное (порядок — порядок выбора). Отметить больше [maxPick] фото
 *   нельзя: рамок сверх лимита всё равно не появится, и лучше не обещать лишнего. Режим не
 *   выводится из [maxPick]: при одном свободном месте окно остаётся с подтверждением;
 * - «Replace photo» ([multi] = `false`): касание сразу выбирает фото и закрывает окно,
 *   подтверждения нет.
 *
 * Список кандидатов собирает вызывающий ([BuilderToolbar]): какие фото уместны — его
 * решение, окно только показывает их и возвращает выбор.
 *
 * Раскладка: три колонки — значения [PhotoGrid] по умолчанию.
 *
 * @param multi `true` — выбор нескольких фото с подтверждением, `false` — одно фото касанием.
 * @param maxPick сколько фото можно отметить (в режиме `multi = false` не используется).
 * @param onConfirm выбранные фото в порядке выбора (при `multi = false` — ровно одно).
 */
@Composable
internal fun PickPhotosDialog(
    title: String,
    candidates: List<PhotoEntity>,
    multi: Boolean,
    maxPick: Int,
    onConfirm: (List<PhotoEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptyList<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (multi) {
                TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        val byId = candidates.associateBy { it.id }
                        onConfirm(picked.mapNotNull { byId[it] })
                    },
                    modifier = Modifier.testTag(DIALOG_TAG_PICK_CONFIRM),
                ) { Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(title) },
        text = {
            PhotoGrid(
                photos = candidates,
                modifier = Modifier.height(320.dp),
                cellTag = { addPhotoCellTag(it.id) },
                cellPadding = 4.dp,
                cellHeight = 90.dp,
                isSelected = { it.id in picked },
                onCellClick = { photo ->
                    if (!multi) {
                        onConfirm(listOf(photo))
                    } else if (photo.id in picked) {
                        picked = picked - photo.id
                    } else if (picked.size < maxPick) {
                        picked = picked + photo.id
                    }
                },
            )
        },
    )
}
