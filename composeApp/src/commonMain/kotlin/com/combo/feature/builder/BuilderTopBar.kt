package com.combo.feature.builder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.ui.common.BackNavIcon

/** Название редактируется на месте, не вытесняя историю и основное действие сохранения. */
@Composable
internal fun BuilderTopBar(state: BuilderUiState, wide: Boolean, onBack: () -> Unit, onEvent: (BuilderEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BackNavIcon(onBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            BasicTextField(
                value = state.name,
                onValueChange = { onEvent(BuilderEvent.SetName(it)) },
                enabled = state.loaded && !state.saving,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().padding(vertical = 8.dp)
                    .testTag(TOOL_TAG_NAME).semantics { contentDescription = "Combo name" },
            )
        }
        if (wide) {
            Text(
                if (state.saving) "Saving…" else if (state.isDirty) "Unsaved changes" else "Saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        BuilderIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", { onEvent(BuilderEvent.Undo) }, enabled = state.canUndo && !state.saving)
        BuilderIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", { onEvent(BuilderEvent.Redo) }, enabled = state.canRedo && !state.saving)
        Spacer(Modifier.width(8.dp))
        Button(
            enabled = state.loaded && !state.saving && state.isDirty,
            onClick = { onEvent(BuilderEvent.Save) },
            modifier = Modifier.testTag(TOOL_TAG_SAVE),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Save")
        }
    }
}
