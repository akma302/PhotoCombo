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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.Const
import com.combo.core.background.BackgroundPresets
import com.combo.core.model.FramingMode
import com.combo.core.render.AspectPresets
import com.combo.core.render.CanvasFormat
import com.combo.feature.builder.draft.FitPreset

/** Что окно выбора фото сейчас делает: добавляет рамки или заменяет фото выбранной. */
private enum class PickMode { NONE, ADD_FRAMES, REPLACE }

/**
 * Нижний тулбар редактора: порядок рамок, режим рамки, пресеты, добавление, замена фото и
 * удаление рамок, формат и фон холста.
 *
 * Полоса горизонтально прокручивается — кнопок больше, чем помещается на экран, и
 * UI-тесты берут нужные по testTag, а не по координатам (см. ComboBuilderGesturesTest про
 * «Fit view» и ComboBuilderToolbarTest про enabled-состояние «Del»).
 */
@Composable
internal fun BuilderToolbar(vm: ComboBuilderViewModel, state: BuilderUiState) {
    var pickMode by remember { mutableStateOf(PickMode.NONE) }
    val sel = state.selectedItemId
    val selItem = state.items.firstOrNull { it.id == sel }
    val selected = selItem != null
    val canAddFrame = state.items.size < Const.MAX_ITEMS
    val canDelete = selected && state.items.size > 1
    val autoFitOn = selItem?.framingMode == FramingMode.FIT

    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(selected, "▲") { vm.onEvent(BuilderEvent.ReorderItem(sel!!, true)) }
            ToolButton(selected, "▼") { vm.onEvent(BuilderEvent.ReorderItem(sel!!, false)) }
            ToolButton(selected, if (state.mode == EditMode.EDIT) "EDIT" else "BORDER") {
                vm.onEvent(BuilderEvent.ToggleMode)
            }
            ToolButton(selected, "Fit") { vm.onEvent(BuilderEvent.ApplyPreset(sel!!, FitPreset.FIT)) }
            ToolButton(selected, "Fill") { vm.onEvent(BuilderEvent.ApplyPreset(sel!!, FitPreset.FILL)) }
            ToolButton(
                selected,
                if (autoFitOn) "AutoFit ON" else "AutoFit OFF",
                TOOL_TAG_AUTOFIT,
            ) {
                val next = if (autoFitOn) FramingMode.FREE else FramingMode.FIT
                sel?.let { vm.onEvent(BuilderEvent.SetFramingMode(it, next)) }
            }
            // «+Frame» = выбрать фото: рамок без фото не бывает.
            ToolButton(canAddFrame, "+Frame", TOOL_TAG_ADD_ITEM) { pickMode = PickMode.ADD_FRAMES }
            ToolButton(selected, "Replace", TOOL_TAG_REPLACE_PHOTO) { pickMode = PickMode.REPLACE }
            ToolButton(canDelete, "Del", TOOL_TAG_DEL) {
                sel?.let { vm.onEvent(BuilderEvent.DeleteItem(it)) }
            }
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
        }
    }

    when (pickMode) {
        PickMode.NONE -> Unit
        PickMode.ADD_FRAMES -> PickPhotosDialog(
            title = "Add frames",
            candidates = state.libraryPhotos,
            multi = true,
            maxPick = (Const.MAX_ITEMS - state.items.size).coerceAtLeast(1),
            onConfirm = { photos ->
                vm.onEvent(BuilderEvent.AddItems(photos.map { it.id }))
                pickMode = PickMode.NONE
            },
            onDismiss = { pickMode = PickMode.NONE },
        )
        PickMode.REPLACE -> if (selItem != null) {
            PickPhotosDialog(
                title = "Replace photo",
                candidates = state.libraryPhotos.filter { it.id != selItem.photoId },
                multi = false,
                maxPick = 1,
                onConfirm = { photos ->
                    vm.onEvent(BuilderEvent.ReplacePhoto(selItem.id, photos.first().id))
                    pickMode = PickMode.NONE
                },
                onDismiss = { pickMode = PickMode.NONE },
            )
        }
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
