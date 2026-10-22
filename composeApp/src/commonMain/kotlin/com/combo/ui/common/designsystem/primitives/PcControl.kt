package com.combo.ui.common.designsystem.primitives

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme

/** Фокус виден у нижней грани; кнопки учитывают способ ввода, поля сохраняют индикацию редактирования. */
@Composable
fun Modifier.pcControlSurface(
    interactionSource: MutableInteractionSource,
    background: Color,
    enabled: Boolean = true,
    borderColor: Color = Color.Transparent,
    shape: Shape = PcTheme.shapes.control,
    focusOnPointer: Boolean = false,
    keepFocusedFill: Boolean = false,
    focusColor: Color = PcTheme.colors.accent,
    glowColor: Color = PcTheme.colors.accent,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val visibleFocus = enabled && focused && (focusOnPointer || keyboard)
    val fill by animateColorAsState(
        if (visibleFocus && !keepFocusedFill && !pressed) PcTheme.colors.focusFill else background,
        tween(PcTheme.motion.effectiveFeedbackMillis),
    )
    val focusProgress by animateFloatAsState(if (visibleFocus) 1f else 0f, tween(PcTheme.motion.effectiveFeedbackMillis))
    val borderWidth = PcTheme.strokes.border
    val focusWidth = PcTheme.strokes.focus
    val focus = PcTheme.focus
    val opacity = if (enabled) 1f else PcTheme.opacity.disabled
    return graphicsLayer { alpha = opacity }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        val lineHeight = focusWidth.toPx().coerceAtMost(size.height)
        val gradientTop = (size.height - focus.gradientHeight.toPx()).coerceAtLeast(0f)
        val inner = Brush.verticalGradient(
            listOf(Color.Transparent, glowColor.copy(alpha = glowColor.alpha * focus.innerAlpha)),
            startY = gradientTop, endY = size.height,
        )
        val haloHeight = focus.haloHeight.toPx()
        val halo = Brush.verticalGradient(
            listOf(glowColor.copy(alpha = glowColor.alpha * focus.haloAlpha), Color.Transparent),
            startY = size.height, endY = size.height + haloHeight,
        )
        onDrawWithContent {
            drawOutline(outline, fill)
            if (enabled && focusProgress > 0f && !keepFocusedFill) clipPath(clip) {
                drawRect(inner, Offset(0f, gradientTop), Size(size.width, size.height - gradientTop), alpha = focusProgress)
            }
            if (borderColor.alpha > 0f) drawOutline(outline, borderColor, style = Stroke(borderWidth.toPx()))
            drawContent()
            if (enabled && focusProgress > 0f) {
                val inset = lineHeight.coerceAtMost(size.width / 2)
                drawRoundRect(halo, Offset(inset, size.height), Size((size.width - inset * 2).coerceAtLeast(0f), haloHeight),
                    cornerRadius = CornerRadius(haloHeight / 2), alpha = focusProgress)
                clipPath(clip) {
                    drawRect(focusColor, Offset(0f, size.height - lineHeight), Size(size.width, lineHeight), alpha = focusProgress)
                }
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
