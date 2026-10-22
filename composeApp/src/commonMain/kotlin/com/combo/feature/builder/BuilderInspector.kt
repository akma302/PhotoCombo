package com.combo.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.combo.core.model.FramingMode
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcCheckbox
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.components.PcSegmentedControl
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.theme.LocalPcContentColor
import com.combo.ui.common.designsystem.theme.PcTheme

/** Одни и те же настройки рамки переносятся вправо или вниз без дублирования действий. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuilderInspector(
    state: BuilderUiState,
    wide: Boolean,
    onEvent: (BuilderEvent) -> Unit,
    onReplace: () -> Unit,
) {
    val item = state.items.firstOrNull { it.id == state.selectedItemId }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 16.dp else 12.dp, vertical = if (wide) 18.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 12.dp),
    ) {
        if (item == null) {
            PcText("Select a frame on the canvas", style = PcTheme.typography.label, color = PcTheme.colors.textSecondary)
            return@Column
        }
        val enabled = !state.saving
        val photo = state.photos[item.photoId]
        val autoFit = item.framingMode == FramingMode.FIT
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PcText("Frame ${state.items.indexOf(item) + 1}", style = PcTheme.typography.title)
                if (wide) PcText(
                    photo?.originalName ?: "Photo", style = PcTheme.typography.caption,
                    color = PcTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BuilderIconButton(PcIcons.Close, "Deselect frame", { onEvent(BuilderEvent.CanvasTapped) }, enabled = enabled)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (wide) 19.dp else 12.dp)) {
            Column(if (wide) Modifier.fillMaxWidth() else Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wide) InspectorLabel("Mouse edits")
                PcSegmentedControl(
                    options = listOf(PcOption(EditMode.EDIT, "Frame", PcIcons.Frame), PcOption(EditMode.BORDER, "Photo", PcIcons.Crop)),
                    value = state.mode,
                    onValueChange = { if (it != state.mode) onEvent(BuilderEvent.ToggleMode) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    optionModifier = { Modifier.testTag(if (it == EditMode.EDIT) TOOL_TAG_MODE_FRAME else TOOL_TAG_MODE_PHOTO) },
                )
            }
            Column(if (wide) Modifier.fillMaxWidth() else Modifier.width(184.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wide) InspectorLabel("Photo placement")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PcButton(
                        "Fit", { onEvent(BuilderEvent.ApplyPreset(item.id, FitPreset.FIT)) },
                        Modifier.weight(1f).testTag(TOOL_TAG_FIT), enabled = enabled,
                    )
                    PcButton(
                        "Fill", { onEvent(BuilderEvent.ApplyPreset(item.id, FitPreset.FILL)) },
                        Modifier.weight(1f).testTag(TOOL_TAG_FILL), enabled = enabled,
                    )
                }
                PcCheckbox(
                    checked = autoFit,
                    onCheckedChange = { onEvent(BuilderEvent.SetFramingMode(item.id, if (it) FramingMode.FIT else FramingMode.FREE)) },
                    label = "Always fit",
                    modifier = Modifier.testTag(TOOL_TAG_AUTOFIT),
                    enabled = enabled,
                )
            }
            FlowRow(
                if (wide) Modifier.fillMaxWidth() else Modifier.width(184.dp),
                horizontalArrangement = if (wide) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (wide) PcButton(
                    "Replace", onReplace,
                    Modifier.testTag(TOOL_TAG_REPLACE_PHOTO).semantics { contentDescription = "Replace photo" },
                    enabled = enabled, icon = PcIcons.ReplacePhoto,
                ) else BuilderIconButton(PcIcons.ReplacePhoto, "Replace photo", onReplace, Modifier.testTag(TOOL_TAG_REPLACE_PHOTO), enabled)
                BuilderIconButton(PcIcons.LayerUp, "Move one layer up", { onEvent(BuilderEvent.ReorderItem(item.id, true)) }, enabled = enabled && state.items.indexOf(item) < state.items.lastIndex)
                BuilderIconButton(PcIcons.LayerDown, "Move one layer down", { onEvent(BuilderEvent.ReorderItem(item.id, false)) }, enabled = enabled && state.items.indexOf(item) > 0)
                CompositionLocalProvider(LocalPcContentColor provides PcTheme.colors.danger) {
                    BuilderIconButton(
                        PcIcons.Delete, "Delete frame", { onEvent(BuilderEvent.DeleteItem(item.id)) },
                        Modifier.testTag(TOOL_TAG_DEL), enabled = enabled && state.items.size > 1,
                    )
                }
            }
        }
        if (wide) {
            PcDivider()
            PcText(
                when {
                    state.mode == EditMode.EDIT -> "Drag to move the frame.\nScroll to resize it."
                    autoFit -> "Drag an edge to resize the frame.\nAlways fit keeps the photo centred."
                    else -> "Drag inside to move the photo.\nDrag an edge to resize the frame."
                },
                style = PcTheme.typography.caption,
                color = PcTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun InspectorLabel(label: String) {
    PcText(label, style = PcTheme.typography.caption, color = PcTheme.colors.textSecondary)
}
