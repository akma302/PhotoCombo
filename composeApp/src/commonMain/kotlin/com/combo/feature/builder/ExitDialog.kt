package com.combo.feature.builder

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.combo.ui.common.RegisterBackHandler

/**
 * Диалог выхода из редактора с несохранёнными изменениями.
 *
 * Все три кнопки гасятся на время сохранения: пока идёт запись в базу, ни «сохранить», ни
 * «всё равно выйти» не должны приводить к двум сохранениям или к выходу с половиной
 * записанного комбо.
 *
 * Сам выход по «Save» происходит НЕ здесь: событие [BuilderEvent.Save] уходит в VM, а
 * экран уходит с экрана по сигналу `saved`. Благодаря этому сообщение «Add at least one
 * photo» показывается snackbar'ом, а редактор остаётся открытым (см. BuilderExitDialogTest).
 * Поэтому [onKeepEditing] и [onDiscard] только закрывают окно, а [onSave] — плюс просьба
 * к VM.
 *
 * Пока диалог в композиции, он лежит верхним обработчиком [RegisterBackHandler]: Esc (и
 * боковая кнопка мыши) закрывают окно как «Keep editing», а не уводят из редактора.
 */
@Composable
internal fun ExitDialog(
    state: BuilderUiState,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    RegisterBackHandler { onKeepEditing() }

    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Save changes?") },
        text = { Text("The combo has unsaved changes.") },
        confirmButton = {
            TextButton(
                enabled = !state.saving,
                modifier = Modifier.testTag(DIALOG_TAG_SAVE),
                onClick = onSave,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !state.saving,
                    modifier = Modifier.testTag(DIALOG_TAG_DISCARD),
                    onClick = onDiscard,
                ) { Text("Discard") }
                TextButton(
                    enabled = !state.saving,
                    modifier = Modifier.testTag(DIALOG_TAG_KEEP),
                    onClick = onKeepEditing,
                ) { Text("Keep editing") }
            }
        },
    )
}
