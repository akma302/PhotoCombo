package com.combo.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.Const
import com.combo.core.background.BackgroundPresets
import com.combo.core.render.AspectPresets

/** Общие настройки холста отделены от действий с выбранной рамкой. */
@Composable
internal fun BuilderCanvasToolbar(state: BuilderUiState, onEvent: (BuilderEvent) -> Unit, onAdd: () -> Unit) {
    var formatMenu by remember { mutableStateOf(false) }
    var backgroundMenu by remember { mutableStateOf(false) }
    val enabled = state.loaded && !state.saving
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            TextButton(onClick = { formatMenu = true }, enabled = enabled, modifier = Modifier.testTag(TOOL_TAG_ASPECT).semantics { contentDescription = "Canvas format" }) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(AspectPresets.label(state.canvasW.toInt(), state.canvasH.toInt()))
            }
            DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                AspectPresets.all.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(AspectPresets.label(format.w, format.h)) },
                        leadingIcon = {
                            if (state.canvasW.toInt() == format.w && state.canvasH.toInt() == format.h) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        onClick = { formatMenu = false; onEvent(BuilderEvent.SetAspect(format.w, format.h)) },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { backgroundMenu = true }, enabled = enabled, modifier = Modifier.testTag(TOOL_TAG_BACKGROUND).semantics { contentDescription = "Canvas background" }) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(BackgroundPresets.label(state.background))
            }
            DropdownMenu(expanded = backgroundMenu, onDismissRequest = { backgroundMenu = false }) {
                BackgroundPresets.cycle.forEach { background ->
                    DropdownMenuItem(
                        text = { Text(BackgroundPresets.label(background)) },
                        leadingIcon = {
                            if (state.background == background) Icon(Icons.Default.Check, contentDescription = "Selected")
                        },
                        onClick = { backgroundMenu = false; onEvent(BuilderEvent.SetBackground(background)) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onAdd, enabled = enabled && state.items.size < Const.MAX_ITEMS, modifier = Modifier.testTag(TOOL_TAG_ADD_ITEM)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Add photo")
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
