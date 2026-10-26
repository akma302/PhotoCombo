package com.combo.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.Const
import com.combo.core.background.BackgroundPresets
import com.combo.core.render.AspectPresets
import com.combo.core.render.CanvasFormat
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.components.PcSelect
import com.combo.ui.common.designsystem.icons.PcIcons

/** Общие настройки холста переносятся целиком, сохраняя состояние открытых меню. */
@Composable
internal fun BuilderCanvasToolbar(state: BuilderUiState, onEvent: (BuilderEvent) -> Unit, onAdd: () -> Unit) {
    val enabled = state.loaded && !state.saving
    val format = CanvasFormat(state.canvasW.toInt(), state.canvasH.toInt())
    // Произвольные сохранённые значения остаются выбранными до явного выбора пресета.
    val formats = if (format in AspectPresets.all) AspectPresets.all else listOf(format) + AspectPresets.all
    val backgrounds = if (state.background in BackgroundPresets.cycle) BackgroundPresets.cycle
        else listOf(state.background) + BackgroundPresets.cycle
    BuilderSettingsLayout(Modifier.fillMaxWidth().background(PcTheme.colors.panel).heightIn(min = 51.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        PcSelect(
            value = format,
            options = formats.map { PcOption(it, AspectPresets.label(it.w, it.h), PcIcons.AspectRatio) },
            onValueChange = { onEvent(BuilderEvent.SetAspect(it.w, it.h)) },
            contentDescription = "Canvas format",
            modifier = Modifier.testTag(TOOL_TAG_ASPECT),
            enabled = enabled,
        )
        PcSelect(
            value = state.background,
            options = backgrounds.map { PcOption(it, BackgroundPresets.label(it), PcIcons.Palette) },
            onValueChange = { onEvent(BuilderEvent.SetBackground(it)) },
            contentDescription = "Canvas background",
            modifier = Modifier.testTag(TOOL_TAG_BACKGROUND),
            enabled = enabled,
        )
        PcButton(
            text = "Add photo",
            onClick = onAdd,
            enabled = enabled && state.items.size < Const.MAX_ITEMS,
            modifier = Modifier.testTag(TOOL_TAG_ADD_ITEM),
            icon = PcIcons.Add,
        )
    }
}

/** Поля сохраняют порядок при переносе, добавление фото прижато к правому краю строки. */
@Composable
private fun BuilderSettingsLayout(modifier: Modifier, content: @Composable () -> Unit) {
    val gap = PcTheme.spacing.md
    val rowGap = PcTheme.spacing.sm
    Layout(content, modifier) { measurables, constraints ->
        val children = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val spacing = gap.roundToPx()
        val width = constraints.constrainWidth(children.sumOf { it.width } + spacing * (children.size - 1).coerceAtLeast(0))
        val positions = mutableListOf<Pair<Int, Int>>()
        var x = 0
        var y = 0
        var rowHeight = 0
        for ((index, child) in children.withIndex()) {
            if (x > 0 && child.width > width - x) {
                y += rowHeight + rowGap.roundToPx()
                x = 0
                rowHeight = 0
            }
            positions += (if (index == children.lastIndex) width - child.width else x) to y
            x += child.width + spacing
            rowHeight = maxOf(rowHeight, child.height)
        }
        layout(width, constraints.constrainHeight(y + rowHeight)) {
            children.forEachIndexed { index, child -> child.placeRelative(positions[index].first, positions[index].second) }
        }
    }
}

/** Диалог расположен вне адаптивной разметки, поэтому выбор переживает её перестройку. */
internal enum class BuilderPickMode { NONE, ADD, REPLACE }

@Composable
internal fun BuilderPhotoPicker(mode: BuilderPickMode, state: BuilderUiState, onEvent: (BuilderEvent) -> Unit, onDismiss: () -> Unit) {
    val selected = state.items.firstOrNull { it.id == state.selectedItemId }
    when (mode) {
        BuilderPickMode.NONE -> Unit
        BuilderPickMode.ADD -> PickPhotosDialog(
            title = "Add frames",
            candidates = state.libraryPhotos,
            multi = true,
            maxPick = (Const.MAX_ITEMS - state.items.size).coerceAtLeast(1),
            onConfirm = { photos -> onEvent(BuilderEvent.AddItems(photos.map { it.id })); onDismiss() },
            onDismiss = onDismiss,
        )
        BuilderPickMode.REPLACE -> if (selected != null) {
            PickPhotosDialog(
                title = "Replace photo",
                candidates = state.libraryPhotos.filter { it.id != selected.photoId },
                multi = false,
                maxPick = 1,
                onConfirm = { photos -> onEvent(BuilderEvent.ReplacePhoto(selected.id, photos.first().id)); onDismiss() },
                onDismiss = onDismiss,
            )
        }
    }
}
