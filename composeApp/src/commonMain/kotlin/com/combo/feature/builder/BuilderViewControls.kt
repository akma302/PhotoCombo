package com.combo.feature.builder

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.Const
import com.combo.core.render.Viewport
import kotlin.math.roundToInt

/** Масштаб вида отделён от параметров фото; сброс недоступен, если вид уже исходный. */
@Composable
internal fun BuilderViewControls(state: BuilderUiState, onEvent: (BuilderEvent) -> Unit) {
    fun zoom(factor: Float) = onEvent(BuilderEvent.ViewportZoom(factor, Offset(state.screenW / 2f, state.screenH / 2f)))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showHint = maxWidth >= 640.dp
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showHint) {
                Text(
                    if (state.mode == EditMode.EDIT) "Drag a frame · Ctrl + scroll to zoom the view"
                    else "Drag a photo · Drag an edge to resize the frame",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else Spacer(Modifier.weight(1f))
            BuilderIconButton(Icons.Default.Remove, "Zoom out view", { zoom(1f / 1.2f) }, Modifier.testTag(TOOL_TAG_ZOOM_OUT), state.loaded && state.viewport.zoom > Const.VIEW_ZOOM_MIN)
            Text("${(state.viewport.zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag(BUILDER_ZOOM_TAG).padding(horizontal = 6.dp))
            BuilderIconButton(Icons.Default.Add, "Zoom in view", { zoom(1.2f) }, Modifier.testTag(TOOL_TAG_ZOOM_IN), state.loaded && state.viewport.zoom < Const.VIEW_ZOOM_MAX)
            TextButton(
                onClick = { onEvent(BuilderEvent.ResetViewport) },
                enabled = state.loaded && state.viewport != Viewport(),
                modifier = Modifier.testTag(TOOL_TAG_FIT_VIEW),
            ) {
                Icon(Icons.Default.FitScreen, contentDescription = null)
                Text("Whole canvas", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
