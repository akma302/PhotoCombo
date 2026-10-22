package com.combo.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.Const
import com.combo.core.render.Viewport
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import kotlin.math.roundToInt

/** Масштаб вида отделён от параметров фото; сброс недоступен, если вид уже исходный. */
@Composable
internal fun BuilderViewControls(state: BuilderUiState, onEvent: (BuilderEvent) -> Unit, onToggleInspector: (() -> Unit)? = null, inspectorExpanded: Boolean = true) {
    fun zoom(factor: Float) = onEvent(BuilderEvent.ViewportZoom(factor, Offset(state.screenW / 2f, state.screenH / 2f)))
    BoxWithConstraints(Modifier.fillMaxWidth().background(PcTheme.colors.panel)) {
        val showHint = maxWidth >= 640.dp
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHint) {
                PcText(
                    if (state.mode == EditMode.EDIT) "Drag a frame · Ctrl + scroll to zoom the view"
                    else "Drag a photo · Drag an edge to resize the frame",
                    style = PcTheme.typography.caption,
                    color = PcTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            } else Spacer(Modifier.weight(1f))
            if (onToggleInspector != null) {
                BuilderIconButton(PcIcons.Frame, if (inspectorExpanded) "Hide inspector" else "Show inspector", onToggleInspector)
            }
            BuilderIconButton(PcIcons.Remove, "Zoom out view", { zoom(1f / 1.2f) }, Modifier.testTag(TOOL_TAG_ZOOM_OUT), state.loaded && state.viewport.zoom > Const.VIEW_ZOOM_MIN)
            PcText("${(state.viewport.zoom * 100).roundToInt()}%", style = PcTheme.typography.control, modifier = Modifier.testTag(BUILDER_ZOOM_TAG).padding(horizontal = 6.dp))
            BuilderIconButton(PcIcons.Add, "Zoom in view", { zoom(1.2f) }, Modifier.testTag(TOOL_TAG_ZOOM_IN), state.loaded && state.viewport.zoom < Const.VIEW_ZOOM_MAX)
            PcButton(
                text = "Whole canvas",
                onClick = { onEvent(BuilderEvent.ResetViewport) },
                enabled = state.loaded && state.viewport != Viewport(),
                modifier = Modifier.testTag(TOOL_TAG_FIT_VIEW),
                variant = PcButtonVariant.Ghost,
                icon = PcIcons.WholeCanvas,
            )
        }
    }
}
