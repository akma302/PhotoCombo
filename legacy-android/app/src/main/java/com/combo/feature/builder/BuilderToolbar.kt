package com.combo.feature.builder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.Const
import com.combo.core.background.BackgroundPresets
import com.combo.core.model.FramingMode
import com.combo.core.model.SwitchMode
import com.combo.core.model.TransitionType
import com.combo.core.render.AspectPresets
import com.combo.core.render.CanvasFormat
import com.combo.core.util.next
import com.combo.feature.builder.draft.FitPreset

/** Всё, что тулбар берёт из выбранной рамки, — считаем один раз. */
private data class ToolbarState(
    val selected: Boolean,
    val canAddPhoto: Boolean,
    val canRemovePhoto: Boolean,
    val canDeleteItem: Boolean,
    val switchMode: SwitchMode?,
    val manualTransition: TransitionType?,
    val autoTransition: TransitionType?,
    val framingMode: FramingMode?,
)

/**
 * Нижний тулбар редактора: порядок рамок, режим рамки, пресеты, фото и параметры перехода.
 *
 * Полоса горизонтально прокручивается — кнопок больше, чем помещается на экран, и
 * UI-тесты берут нужные по testTag, а не по координатам (см. ComboBuilderGesturesTest про
 * «Fit view» и ComboBuilderToolbarTest про enabled-состояние «Del»).
 */
@Composable
internal fun BuilderToolbar(vm: ComboBuilderViewModel, state: BuilderUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    val sel = state.selectedItemId
    val selItem = state.items.firstOrNull { it.id == sel }
    val toolbar = ToolbarState(
        selected = sel != null,
        canAddPhoto = sel != null && (selItem?.contents?.size ?: 0) < Const.MAX_CONTENTS_PER_ITEM,
        canRemovePhoto = sel != null && (selItem?.contents?.size ?: 0) > 1,
        canDeleteItem = sel != null && state.items.size > 1,
        switchMode = selItem?.switchMode,
        manualTransition = selItem?.manualTransition,
        autoTransition = selItem?.autoTransition,
        framingMode = selItem?.framingMode,
    )
    val autoFitOn = toolbar.framingMode == FramingMode.FIT

    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(toolbar.selected, "▲") { vm.onEvent(BuilderEvent.ReorderItem(sel!!, true)) }
            ToolButton(toolbar.selected, "▼") { vm.onEvent(BuilderEvent.ReorderItem(sel!!, false)) }
            ToolButton(toolbar.selected, if (state.mode == EditMode.EDIT) "EDIT" else "BORDER") {
                vm.onEvent(BuilderEvent.ToggleMode)
            }
            ToolButton(toolbar.selected, "Fit") { vm.onEvent(BuilderEvent.ApplyPreset(sel!!, FitPreset.FIT)) }
            ToolButton(toolbar.selected, "Fill") { vm.onEvent(BuilderEvent.ApplyPreset(sel!!, FitPreset.FILL)) }
            ToolButton(
                toolbar.selected,
                if (autoFitOn) "AutoFit ON" else "AutoFit OFF",
                TOOL_TAG_AUTOFIT,
            ) {
                val next = if (autoFitOn) FramingMode.FREE else FramingMode.FIT
                sel?.let { vm.onEvent(BuilderEvent.SetFramingMode(it, next)) }
            }
            ToolButton(
                toolbar.canAddPhoto,
                "+Photo",
                TOOL_TAG_ADD_PHOTO,
            ) { showAddDialog = true }
            ToolButton(
                toolbar.canRemovePhoto,
                "−Photo",
            ) { sel?.let { vm.onEvent(BuilderEvent.RemoveActiveContent(it)) } }
            ToolButton(
                toolbar.canDeleteItem,
                "Del",
                TOOL_TAG_DEL,
            ) { sel?.let { vm.onEvent(BuilderEvent.DeleteItem(it)) } }
            // Сброс зума и сдвига вида; сам холст, правки и undo не затрагивает.
            ToolButton(true, "Fit view", TOOL_TAG_FIT_VIEW) {
                vm.onEvent(BuilderEvent.ResetViewport)
            }
            // Формат читается из состояния холста, а не из локального индекса: открытое комбо
            // 1200×1200 обязано показывать «1:1», а не первый пресет.
            ToolButton(
                true,
                "Aspect ${AspectPresets.label(state.canvasW.toInt(), state.canvasH.toInt())}",
                TOOL_TAG_ASPECT,
            ) {
                val next = AspectPresets.next(CanvasFormat(state.canvasW.toInt(), state.canvasH.toInt()))
                vm.onEvent(BuilderEvent.SetAspect(next.w, next.h))
            }
            ToolButton(true, "BG ${BackgroundPresets.label(state.background)}") {
                vm.onEvent(BuilderEvent.SetBackground(BackgroundPresets.next(state.background)))
            }
            // Следующее значение берётся через next(): порядок перебора задаёт порядок
            // объявления значений в enum'е (его проверяет EnumCycleTest).
            ToolButton(toolbar.selected, "Switch: ${toolbar.switchMode?.name ?: "-"}") {
                vm.onEvent(
                    BuilderEvent.SetSwitchMode((toolbar.switchMode ?: SwitchMode.MANUAL).next())
                )
            }
            ToolButton(toolbar.selected, "M-Tr: ${toolbar.manualTransition?.name ?: "-"}") {
                vm.onEvent(
                    BuilderEvent.SetManualTransition((toolbar.manualTransition ?: TransitionType.NONE).next())
                )
            }
            ToolButton(toolbar.selected, "A-Tr: ${toolbar.autoTransition?.name ?: "-"}") {
                vm.onEvent(
                    BuilderEvent.SetAutoTransition((toolbar.autoTransition ?: TransitionType.NONE).next())
                )
            }
        }
    }

    val addItem = selItem
    if (showAddDialog && sel != null && addItem != null) {
        val itemId = sel
        val existing = addItem.contents.map { it.photoId }.toSet()
        AddPhotoDialog(
            item = addItem,
            candidates = state.libraryPhotos.filter { it.id !in existing },
            onPick = { photo ->
                vm.onEvent(BuilderEvent.AddPhotos(itemId, listOf(photo.id)))
                // item.contents — мутабельный драфт и к этому моменту уже содержит
                // добавленное фото, поэтому размер сравнивается с пределом без «+1».
                if (addItem.contents.size >= Const.MAX_CONTENTS_PER_ITEM) {
                    showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun ToolButton(enabled: Boolean, label: String, tag: String? = null, onClick: () -> Unit) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    ) { Text(label) }
}