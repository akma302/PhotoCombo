package com.combo.feature.builder

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.data.PhotoEntity
import com.combo.ui.common.ThumbImage
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcDialog
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

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
 * Список кандидатов собирает вызывающий ([BuilderPhotoPicker]): какие фото уместны — его
 * решение, окно только показывает их и возвращает выбор.
 *
 * Три колонки сохраняют стабильные ключи фото и отметки при изменении размера окна.
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

    PcDialog(
        onDismissRequest = onDismiss,
        title = title,
        actions = {
            PcButton(text = "Cancel", onClick = onDismiss, variant = PcButtonVariant.Ghost)
            if (multi) {
                PcButton(
                    text = if (picked.isEmpty()) "Add" else "Add ${picked.size}",
                    variant = PcButtonVariant.Primary,
                    icon = PcIcons.Add,
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        val byId = candidates.associateBy { it.id }
                        onConfirm(picked.mapNotNull { byId[it] })
                    },
                    modifier = Modifier.testTag(DIALOG_TAG_PICK_CONFIRM),
                )
            }
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm),
        ) {
            items(candidates, key = { it.id }) { photo ->
                val selected = photo.id in picked
                PickPhotoCell(photo, selected, multi) {
                    when {
                        !multi -> onConfirm(listOf(photo))
                        selected -> picked = picked - photo.id
                        picked.size < maxPick -> picked = picked + photo.id
                    }
                }
            }
        }
    }
}

/** Миниатюра целиком доступна с клавиатуры; отметка не создаёт второго действия. */
@Composable
private fun PickPhotoCell(photo: PhotoEntity, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val gesture = if (multi) {
        Modifier.toggleable(selected, source, indication = null, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick)
    }
    Box(
        Modifier.fillMaxWidth().height(90.dp)
            .pcControlSurface(source, PcTheme.colors.workspace)
            .then(gesture)
            .semantics { contentDescription = photo.originalName ?: photo.fileName }
            .testTag(addPhotoCellTag(photo.id)),
    ) {
        ThumbImage(photo.id, Modifier.matchParentSize(), placeholderColor = PcTheme.colors.workspace)
        if (selected) {
            Box(Modifier.matchParentSize().border(PcTheme.strokes.selection, PcTheme.colors.accent))
            PcSurface(
                modifier = Modifier.align(Alignment.TopEnd).padding(PcTheme.spacing.xs),
                color = PcTheme.colors.accent,
                contentColor = PcTheme.colors.onAccent,
                shape = PcTheme.shapes.small,
            ) {
                PcIcon(PcIcons.Check, modifier = Modifier.padding(PcTheme.spacing.xs))
            }
        }
    }
}
