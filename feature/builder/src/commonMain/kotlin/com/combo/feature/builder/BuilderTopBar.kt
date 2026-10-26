package com.combo.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcTextField
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme

/** Название редактируется на месте, не вытесняя историю и основное действие сохранения. */
@Composable
internal fun BuilderTopBar(state: BuilderUiState, wide: Boolean, onBack: () -> Unit, onEvent: (BuilderEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PcTheme.colors.panel).heightIn(min = 57.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuilderIconButton(PcIcons.Back, "Back", onBack)
        Box((if (wide) Modifier.width(190.dp) else Modifier.weight(1f)).padding(horizontal = 8.dp)) {
            PcTextField(
                value = state.name,
                onValueChange = { onEvent(BuilderEvent.SetName(it)) },
                label = "Combo name",
                enabled = state.loaded && !state.saving,
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().testTag(TOOL_TAG_NAME),
                showLabel = false,
            )
        }
        if (wide) {
            PcText(
                if (state.saving) "Saving…" else if (state.isDirty) "Unsaved changes" else "Saved",
                style = PcTheme.typography.caption,
                color = PcTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (wide) Spacer(Modifier.weight(1f))
        BuilderIconButton(PcIcons.Undo, "Undo", { onEvent(BuilderEvent.Undo) }, enabled = state.canUndo && !state.saving)
        BuilderIconButton(PcIcons.Redo, "Redo", { onEvent(BuilderEvent.Redo) }, enabled = state.canRedo && !state.saving)
        Spacer(Modifier.width(8.dp))
        PcButton(
            text = "Save",
            enabled = state.loaded && !state.saving && state.isDirty,
            busy = state.saving,
            onClick = { onEvent(BuilderEvent.Save) },
            modifier = Modifier.testTag(TOOL_TAG_SAVE),
            variant = PcButtonVariant.Primary,
            icon = PcIcons.Check,
        )
    }
}
