package com.combo.ui.common.designsystem.primitives

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme

/** Контур фокуса рисуется снаружи поверхности и не меняет её геометрию. */
@Composable
fun Modifier.pcControlSurface(
    interactionSource: MutableInteractionSource,
    background: Color,
    enabled: Boolean = true,
    borderColor: Color = Color.Transparent,
    shape: Shape = PcTheme.shapes.control,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val fill by animateColorAsState(background, tween(PcTheme.motion.effectiveFeedbackMillis))
    val borderWidth = PcTheme.strokes.border
    val focusWidth = PcTheme.strokes.focus
    val focusOffset = PcTheme.sizes.focusOffset
    val accent = PcTheme.colors.accent
    val opacity = if (enabled) 1f else PcTheme.opacity.disabled
    return graphicsLayer { alpha = opacity }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val gap = focusOffset.toPx() + focusWidth.toPx() / 2
        val outer = shape.createOutline(
            androidx.compose.ui.geometry.Size(size.width + gap * 2, size.height + gap * 2), layoutDirection, this,
        )
        onDrawWithContent {
            drawOutline(outline, fill)
            if (borderColor.alpha > 0f) drawOutline(outline, borderColor, style = Stroke(borderWidth.toPx()))
            drawContent()
            if (focused && enabled) translate(-gap, -gap) {
                drawOutline(outer, accent, style = Stroke(focusWidth.toPx()))
            }
        }
    }
}

/** Одно содержимое измеряется до применения минимума; длинный текст увеличивает контрол. */
@Composable
fun PcControlLayout(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = PcTheme.spacing.md,
    verticalPadding: Dp = PcTheme.spacing.xs,
    minimumWidth: Dp = PcTheme.minimumTarget,
    content: @Composable () -> Unit,
) {
    val minimumTarget = PcTheme.minimumTarget
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == 1) { "PcControlLayout expects one content root" }
        val px = horizontalPadding.roundToPx()
        val py = verticalPadding.roundToPx()
        val child = measurables.single().measure(constraints.offset(-2 * px, -2 * py).copy(minWidth = 0, minHeight = 0))
        val width = constraints.constrainWidth(maxOf(minimumWidth.roundToPx(), child.width + px * 2))
        val height = constraints.constrainHeight(maxOf(minimumTarget.roundToPx(), child.height + py * 2))
        layout(width, height) { child.placeRelative((width - child.width) / 2, (height - child.height) / 2) }
    }
}
