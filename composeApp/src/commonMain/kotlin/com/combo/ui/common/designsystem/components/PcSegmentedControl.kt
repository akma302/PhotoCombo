package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.zIndex
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

/** Равные по высоте сегменты выбирают значение; направление стрелок следует направлению UI. */
@Composable
fun <T> PcSegmentedControl(
    options: List<PcOption<T>>,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.isNotEmpty()) { "PcSegmentedControl needs at least one option" }
    require(options.map { it.value }.distinct().size == options.size) { "Option values must be distinct" }
    require(options.any { it.value == value }) { "Selected value must be present in options" }
    val requesters = remember(options.map { it.value }) { List(options.size) { FocusRequester() } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inputMode = LocalInputModeManager.current
    val colors = PcTheme.colors
    val minimumTarget = PcTheme.minimumTarget
    Layout(
        modifier = modifier.selectableGroup().background(colors.app, PcTheme.shapes.group),
        content = {
            options.forEachIndexed { index, option ->
                val source = remember(option.value) { MutableInteractionSource() }
                val hovered by source.collectIsHoveredAsState()
                val pressed by source.collectIsPressedAsState()
                val focused by source.collectIsFocusedAsState()
                val selected = option.value == value
                val background = when {
                    !enabled -> if (selected) colors.panel else colors.app
                    pressed -> colors.pressed
                    hovered -> colors.hover
                    selected -> colors.panel
                    else -> colors.app
                }
                val textColor = if (selected) colors.accent else colors.text
                Row(
                    modifier = Modifier
                        .zIndex(if (focused) 1f else 0f)
                        .focusRequester(requesters[index])
                        .onPreviewKeyEvent { event ->
                            val direction = when (event.key) {
                                Key.DirectionRight -> if (rtl) -1 else 1
                                Key.DirectionLeft -> if (rtl) 1 else -1
                                else -> 0
                            }
                            if (!enabled || direction == 0) false else {
                                if (event.type == KeyEventType.KeyDown) {
                                    inputMode.requestInputMode(InputMode.Keyboard)
                                    val next = (index + direction + options.size) % options.size
                                    requesters[next].requestFocus()
                                    if (options[next].value != value) onValueChange(options[next].value)
                                }
                                true
                            }
                        }
                        .selectable(
                            selected = selected,
                            interactionSource = source,
                            indication = null,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onValueChange(option.value) },
                        )
                        .pcControlSurface(source, background, enabled)
                        .padding(horizontal = PcTheme.spacing.md, vertical = PcTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    option.icon?.let {
                        PcIcon(it, tint = textColor)
                        Spacer(Modifier.width(PcTheme.spacing.sm))
                    }
                    PcText(
                        option.label,
                        color = textColor,
                        style = if (selected) PcTheme.typography.controlStrong else PcTheme.typography.control,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val target = minimumTarget.roundToPx()
        val widest = measurables.maxOf { maxOf(target, it.maxIntrinsicWidth(Constraints.Infinity)) }
        val naturalWidth = (widest.toLong() * measurables.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val width = constraints.constrainWidth(naturalWidth)
        val baseWidth = width / measurables.size
        val remainder = width % measurables.size
        val widths = measurables.indices.map { baseWidth + if (it < remainder) 1 else 0 }
        val height = constraints.constrainHeight(maxOf(target, measurables.indices.maxOf {
            measurables[it].maxIntrinsicHeight(widths[it])
        }))
        val children = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(widths[index], height))
        }
        layout(width, height) {
            var x = 0
            children.forEach { child ->
                child.placeRelative(x, 0)
                x += child.width
            }
        }
    }
}
