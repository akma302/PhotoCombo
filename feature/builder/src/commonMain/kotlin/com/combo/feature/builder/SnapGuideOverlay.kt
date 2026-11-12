package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.combo.core.render.CanvasTransform
import com.combo.core.render.snap.SnapAxis

internal fun DrawScope.drawSnapGuideOverlay(
    state: BuilderUiState,
    transform: CanvasTransform,
    color: Color,
    outline: Color,
) {
    if (state.snapGuides.isEmpty()) return
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
    val corner = transform.toScreen(Offset(state.canvasW, state.canvasH))
    clipRect(transform.origin.x, transform.origin.y, corner.x, corner.y) {
        for (guide in state.snapGuides) {
            val start = if (guide.axis == SnapAxis.X) Offset(guide.coordinate, guide.start)
                else Offset(guide.start, guide.coordinate)
            val end = if (guide.axis == SnapAxis.X) Offset(guide.coordinate, guide.end)
                else Offset(guide.end, guide.coordinate)
            val from = transform.toScreen(start)
            val to = transform.toScreen(end)
            val opacity = if (guide.preview) 0.48f else 1f
            drawLine(outline.copy(alpha = opacity), from, to, 3.dp.toPx(), pathEffect = dash)
            drawLine(color.copy(alpha = opacity), from, to, 1.5.dp.toPx(), pathEffect = dash)
        }
    }
}
