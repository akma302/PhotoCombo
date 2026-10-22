package com.combo.ui.common.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

enum class PcButtonVariant { Primary, Secondary, Ghost, Danger }

/** Кнопка сохраняет размер и доступное имя во время выполнения действия. */
@Composable
fun PcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PcButtonVariant = PcButtonVariant.Secondary,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = PcTheme.colors
    val primary = variant == PcButtonVariant.Primary
    val normal = when (variant) {
        PcButtonVariant.Primary -> colors.accent
        PcButtonVariant.Secondary -> colors.panel
        PcButtonVariant.Ghost, PcButtonVariant.Danger -> Color.Transparent
    }
    val background = when {
        !enabled || busy -> normal
        pressed -> if (primary) lerp(colors.accent, colors.onAccent, 0.12f) else colors.pressed
        hovered -> if (primary) lerp(colors.accent, colors.text, 0.08f) else colors.hover
        else -> normal
    }
    val foreground = when (variant) {
        PcButtonVariant.Primary -> colors.onAccent
        PcButtonVariant.Danger -> colors.danger
        else -> colors.text
    }
    PcControlLayout(
        modifier.pcControlSurface(
            interactionSource, background, enabled,
            borderColor = if (variant == PcButtonVariant.Secondary) colors.border else Color.Transparent,
            keepFocusedFill = primary,
            focusColor = if (primary) colors.onAccent else colors.accent,
        ).clickable(interactionSource = interactionSource, indication = null,
            enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .semantics { if (busy) stateDescription = "Busy" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(Modifier.alpha(if (busy) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                if (icon != null) PcIcon(icon, tint = foreground)
                PcText(text, color = foreground,
                    style = if (primary) PcTheme.typography.controlStrong else PcTheme.typography.control)
            }
            if (busy) {
                Box(Modifier.matchParentSize().wrapContentSize(unbounded = true),
                    contentAlignment = Alignment.Center) {
                    PcBusyIndicator(foreground)
                }
            }
        }
    }
}

/** При уменьшенном движении остаётся статичная дуга; смысл состояния передаётся семантикой кнопки. */
@Composable
internal fun PcBusyIndicator(color: Color) {
    val rotation = if (PcTheme.motion.reducedMotion) 0f else {
        val transition = rememberInfiniteTransition()
        val angle by transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart))
        angle
    }
    Canvas(Modifier.size(PcTheme.sizes.icon)) {
        val stroke = size.minDimension / 10f
        val inset = stroke / 2f
        drawArc(color, rotation - 90f, 270f, false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
