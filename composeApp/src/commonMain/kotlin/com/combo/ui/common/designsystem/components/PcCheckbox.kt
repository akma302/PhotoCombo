package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

/** Флажок и подпись образуют одну область ввода и один семантический узел. */
@Composable
fun PcCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = PcTheme.colors
    val borderWidth = PcTheme.strokes.border
    val checkWidth = PcTheme.strokes.selection
    val shape = PcTheme.shapes.small
    val background = when {
        !enabled -> Color.Transparent
        pressed -> colors.pressed
        hovered -> colors.hover
        else -> Color.Transparent
    }
    PcControlLayout(
        modifier = modifier
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .pcControlSurface(source, background, enabled),
        horizontalPadding = PcTheme.spacing.sm,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(PcTheme.sizes.icon)) {
                val radius = shape.topStart.toPx(size, this)
                val inset = borderWidth.toPx() / 2
                drawRoundRect(
                    color = if (checked) colors.accent else colors.app,
                    cornerRadius = CornerRadius(radius),
                )
                if (!checked) drawRoundRect(
                    color = colors.textSecondary,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                    style = Stroke(borderWidth.toPx()),
                )
                if (checked) {
                    val start = Offset(size.width * 0.24f, size.height * 0.51f)
                    val corner = Offset(size.width * 0.43f, size.height * 0.7f)
                    val end = Offset(size.width * 0.77f, size.height * 0.32f)
                    drawLine(colors.onAccent, start, corner, checkWidth.toPx(), StrokeCap.Round)
                    drawLine(colors.onAccent, corner, end, checkWidth.toPx(), StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(PcTheme.spacing.sm))
            PcText(label, color = colors.text, style = PcTheme.typography.control)
        }
    }
}
