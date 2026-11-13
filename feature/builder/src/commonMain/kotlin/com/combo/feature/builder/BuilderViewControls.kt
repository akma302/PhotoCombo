package com.combo.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BuilderViewControls(
    state: BuilderUiState,
    onEvent: (BuilderEvent) -> Unit,
    onResizeCanvas: () -> Unit,
    onToggleInspector: (() -> Unit)? = null,
    inspectorExpanded: Boolean = true,
) {
    fun zoom(factor: Float) = onEvent(BuilderEvent.ViewportZoom(factor, Offset(state.screenW / 2f, state.screenH / 2f)))
    val viewLabel = if (state.loaded && state.screenW > 0f && state.screenH > 0f) {
        val scale = state.viewport.scale(state.canvasSize, state.viewportSize)
        val percent = (scale * 100).roundToInt()
        if (percent == 0) "View <1%" else "View $percent%"
    } else "View —"
    BoxWithConstraints(Modifier.fillMaxWidth().background(PcTheme.colors.panel)) {
        val showHint = maxWidth >= 900.dp
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.loaded) {
                PcButton(
                    text = "Canvas ${state.canvasW.toInt()} × ${state.canvasH.toInt()} px",
                    onClick = onResizeCanvas,
                    enabled = !state.saving,
                    icon = PcIcons.Edit,
                    variant = PcButtonVariant.Ghost,
                )
            }
            if (showHint) {
                val hint = if (state.mode == EditMode.BORDER) {
                    "Shift + drag an edge to resize one side · Ctrl + drag for free placement"
                } else {
                    "Ctrl + drag for free placement · Space + scroll for view zoom"
                }
                PcText(
                    hint,
                    style = PcTheme.typography.caption,
                    color = PcTheme.colors.textSecondary,
                )
            }
            if (onToggleInspector != null) {
                BuilderIconButton(PcIcons.Frame, if (inspectorExpanded) "Hide inspector" else "Show inspector", onToggleInspector)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuilderIconButton(
                    PcIcons.Remove, "Zoom out view", { zoom(1f / 1.2f) },
                    Modifier.testTag(TOOL_TAG_ZOOM_OUT), state.loaded && state.viewport.zoom > Const.VIEW_ZOOM_MIN,
                )
                PcText(
                    viewLabel,
                    modifier = Modifier.testTag(BUILDER_ZOOM_TAG).padding(horizontal = 6.dp),
                    style = PcTheme.typography.control,
                )
                BuilderIconButton(
                    PcIcons.Add, "Zoom in view", { zoom(1.2f) },
                    Modifier.testTag(TOOL_TAG_ZOOM_IN), state.loaded && state.viewport.zoom < Const.VIEW_ZOOM_MAX,
                )
            }
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
