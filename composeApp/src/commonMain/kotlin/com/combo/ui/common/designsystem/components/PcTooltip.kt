package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import kotlinx.coroutines.delay

/** Подсказка не забирает фокус и не участвует в обработке клика инициатора. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PcTooltip(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val delayMillis = PcTheme.motion.tooltipDelayMillis
    val gap = with(LocalDensity.current) { PcTheme.spacing.sm.roundToPx() }
    LaunchedEffect(hovered, focused, enabled, delayMillis) {
        visible = false
        if (enabled && (hovered || focused)) {
            delay(delayMillis.toLong())
            visible = true
        }
    }
    Box(modifier.onPointerEvent(PointerEventType.Enter) { hovered = true }
        .onPointerEvent(PointerEventType.Exit) { hovered = false }
        .onFocusChanged { focused = it.hasFocus }.focusGroup()) {
        content()
        if (visible) {
            Popup(popupPositionProvider = remember(gap) { PcAnchorPosition(gap, above = true) },
                properties = PopupProperties(focusable = false)) {
                PcSurface(shape = PcTheme.shapes.small, borderColor = PcTheme.colors.border,
                    borderWidth = PcTheme.strokes.border) {
                    PcText(text, Modifier.padding(horizontal = PcTheme.spacing.sm, vertical = PcTheme.spacing.xs),
                        style = PcTheme.typography.caption)
                }
            }
        }
    }
}

/** Координаты остаются в окне, в RTL список выравнивается по правому краю. */
internal class PcAnchorPosition(private val gap: Int, private val above: Boolean = false) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val left = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val upper = anchorBounds.top - popupContentSize.height - gap
        val lower = anchorBounds.bottom + gap
        val preferred = if (above && upper >= 0) upper else if (lower + popupContentSize.height <= windowSize.height) lower else upper
        return IntOffset(left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            preferred.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}
