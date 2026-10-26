package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

/** Управляемый ползунок: значение меняется при вводе, завершение сообщает о необходимости сохранения. */
@Composable
fun PcSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(valueRange.start.isFinite() && valueRange.endInclusive.isFinite() && valueRange.start < valueRange.endInclusive)
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange))
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val source = remember { MutableInteractionSource() }
    val focus = remember { FocusRequester() }
    val hovered by source.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val colors = PcTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun set(next: Float): Boolean {
        if (!enabled || !next.isFinite()) return false
        val bounded = next.coerceIn(valueRange)
        if (bounded == currentValue) return false
        change(bounded)
        finish()
        return true
    }
    // Контейнер принимает ввод, дочерний Canvas отвечает только за рисунок.
    Box(
        modifier.widthIn(min = 80.dp).heightIn(min = PcTheme.minimumTarget)
            .pcControlSurface(source, when {
                dragging -> colors.pressed
                hovered -> colors.hover
                else -> colors.panel
            }, enabled)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(currentValue, valueRange)
                if (!enabled) disabled()
                setProgress { set(it) }
            }
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) false else {
                    val next = when (event.key) {
                        Key.DirectionRight -> currentValue + if (rtl) -1f else 1f
                        Key.DirectionLeft -> currentValue + if (rtl) 1f else -1f
                        Key.DirectionUp -> currentValue + 1f
                        Key.DirectionDown -> currentValue - 1f
                        Key.MoveHome -> valueRange.start
                        Key.MoveEnd -> valueRange.endInclusive
                        else -> return@onKeyEvent false
                    }
                    set(next)
                    true
                }
            }
            .focusable(enabled, source)
            .hoverable(source, enabled)
            .pointerInput(enabled, valueRange, rtl) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                    focus.requestFocus()
                    val before = currentValue
                    var completed = false
                    fun update(x: Float) {
                        val inset = 12.dp.toPx().coerceAtMost(size.width / 2f)
                        val fraction = ((x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        change(valueRange.start + (if (rtl) 1f - fraction else fraction) * (valueRange.endInclusive - valueRange.start))
                    }
                    dragging = true
                    try {
                        down.consume()
                        update(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (pointer.isConsumed) break
                            update(pointer.position.x)
                            pointer.consume()
                            if (!pointer.pressed) {
                                completed = true
                                finish()
                                break
                            }
                        }
                    } finally {
                        dragging = false
                        if (!completed) change(before)
                    }
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 12.dp.toPx().coerceAtMost(size.width / 2f)
            val fraction = (currentValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            val start = Offset(if (rtl) size.width - inset else inset, size.height / 2)
            val end = Offset(if (rtl) inset else size.width - inset, size.height / 2)
            val thumb = Offset(start.x + (end.x - start.x) * fraction, start.y)
            drawLine(colors.border, start, end, 4.dp.toPx(), StrokeCap.Round)
            drawLine(colors.accent, start, thumb, 4.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.accent, 7.dp.toPx(), thumb)
        }
    }
}
