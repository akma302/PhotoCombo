package com.combo.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.combo.core.model.FramingMode
import com.combo.core.render.TransformMath
import com.combo.data.srcSize
import com.combo.feature.builder.draft.FitPreset
import kotlin.math.abs

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
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(if (wide) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (item == null) {
            Text("Select a frame on the canvas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val enabled = !state.saving
        val photo = state.photos[item.photoId]
        val autoFit = item.framingMode == FramingMode.FIT
        val src = photo?.srcSize()
        val fitted = src?.let { TransformMath.fitScale(item.border(), it) }
        val filled = src?.let { TransformMath.fillScale(item.border(), it) }
        val centred = abs(item.panX) < 0.001f && abs(item.panY) < 0.001f
        val fitSelected = autoFit || (centred && fitted != null && fitted != filled && abs(item.contentScale - fitted) < 0.0001f)
        val fillSelected = !autoFit && centred && filled != null && abs(item.contentScale - filled) < 0.0001f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Frame ${state.items.indexOf(item) + 1}", style = MaterialTheme.typography.titleSmall)
                if (wide) Text(photo?.originalName ?: "Photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BuilderIconButton(Icons.Default.Close, "Deselect frame", { onEvent(BuilderEvent.CanvasTapped) }, enabled = enabled)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.width(if (wide) 232.dp else 170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wide) InspectorLabel("Mouse edits")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(EditMode.EDIT to "Frame", EditMode.BORDER to "Photo").forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { if (state.mode != mode) onEvent(BuilderEvent.ToggleMode) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            enabled = enabled,
                            icon = {},
                            modifier = Modifier.testTag(if (mode == EditMode.EDIT) TOOL_TAG_MODE_FRAME else TOOL_TAG_MODE_PHOTO),
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Column(Modifier.width(if (wide) 232.dp else 154.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wide) InspectorLabel("Photo placement")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(FitPreset.FIT to "Fit", FitPreset.FILL to "Fill").forEachIndexed { index, (preset, label) ->
                        SegmentedButton(
                            selected = if (preset == FitPreset.FIT) fitSelected else fillSelected,
                            onClick = { onEvent(BuilderEvent.ApplyPreset(item.id, preset)) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            enabled = enabled,
                            icon = {},
                            modifier = Modifier.testTag(if (preset == FitPreset.FIT) TOOL_TAG_FIT else TOOL_TAG_FILL),
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Row(
                Modifier.width(if (wide) 232.dp else 170.dp)
                    .testTag(TOOL_TAG_AUTOFIT)
                    .toggleable(value = autoFit, enabled = enabled, role = Role.Checkbox) {
                        onEvent(BuilderEvent.SetFramingMode(item.id, if (it) FramingMode.FIT else FramingMode.FREE))
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = autoFit, onCheckedChange = null, enabled = enabled)
                Spacer(Modifier.width(8.dp))
                Text("Always fit", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuilderIconButton(Icons.Default.Image, "Replace photo", onReplace, Modifier.testTag(TOOL_TAG_REPLACE_PHOTO), enabled)
                BuilderIconButton(Icons.Default.FlipToFront, "Move one layer up", { onEvent(BuilderEvent.ReorderItem(item.id, true)) }, enabled = enabled && state.items.indexOf(item) < state.items.lastIndex)
                BuilderIconButton(Icons.Default.FlipToBack, "Move one layer down", { onEvent(BuilderEvent.ReorderItem(item.id, false)) }, enabled = enabled && state.items.indexOf(item) > 0)
                BuilderIconButton(Icons.Default.DeleteOutline, "Delete frame", { onEvent(BuilderEvent.DeleteItem(item.id)) }, Modifier.testTag(TOOL_TAG_DEL), enabled && state.items.size > 1, MaterialTheme.colorScheme.error)
            }
        }
        if (wide) {
            Text(
                when {
                    state.mode == EditMode.EDIT -> "Drag to move the frame.\nScroll to resize it."
                    autoFit -> "Drag an edge to resize the frame.\nAlways fit keeps the photo centred."
                    else -> "Drag inside to move the photo.\nDrag an edge to resize the frame."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InspectorLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
