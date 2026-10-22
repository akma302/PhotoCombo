package com.combo.feature.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.combo.ui.common.RegisterBackHandler
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcDialog
import com.combo.ui.common.designsystem.primitives.PcText

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

    PcDialog(
        onDismissRequest = onKeepEditing,
        title = "Save changes?",
        actions = {
            PcButton(
                text = "Keep editing",
                enabled = !state.saving,
                modifier = Modifier.testTag(DIALOG_TAG_KEEP),
                onClick = onKeepEditing,
                variant = PcButtonVariant.Ghost,
            )
            PcButton(
                text = "Discard",
                enabled = !state.saving,
                modifier = Modifier.testTag(DIALOG_TAG_DISCARD),
                onClick = onDiscard,
                variant = PcButtonVariant.Danger,
            )
            PcButton(
                text = "Save",
                enabled = !state.saving,
                busy = state.saving,
                modifier = Modifier.testTag(DIALOG_TAG_SAVE),
                onClick = onSave,
                variant = PcButtonVariant.Primary,
            )
        },
    ) { PcText("The combo has unsaved changes.") }
}
