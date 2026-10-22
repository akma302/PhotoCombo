package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.primitives.pcPopupAppearance
import com.combo.ui.common.designsystem.theme.PcTheme

/** Значение принадлежит вызывающему; меню хранит только раскрытие и позицию клавиатурного выбора. */
@Composable
fun <T> PcSelect(
    value: T,
    options: List<PcOption<T>>,
    onValueChange: (T) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.isNotEmpty()) { "PcSelect requires options" }
    require(options.map { it.value }.distinct().size == options.size) { "Option values must be distinct" }
    val selected = options.indexOfFirst { it.value == value }
    require(selected >= 0) { "Selected value must be present in options" }
    require(contentDescription.isNotBlank()) { "PcSelect requires an accessible name" }
    var expanded by remember { mutableStateOf(false) }
    var activeValue by remember { mutableStateOf(value) }
    val active = options.indexOfFirst { it.value == activeValue }.takeIf { it >= 0 } ?: selected
    var anchorWidth by remember { mutableIntStateOf(0) }
    var restoreFocus by remember { mutableStateOf(false) }
    val anchorFocus = remember { FocusRequester() }
    val optionFocus = remember(options.map { it.value }) { List(options.size) { FocusRequester() } }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = PcTheme.colors
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val gap = with(density) { PcTheme.spacing.xs.roundToPx() }
    val maxWidth = with(density) { window.width.toDp().coerceAtLeast(1.dp) }
    val maxHeight = with(density) { window.height.toDp().coerceAtLeast(1.dp) }
    val menuMinWidth = with(density) { anchorWidth.toDp() }.coerceAtMost(maxWidth)
    val menuMaxWidth = minOf(360.dp, maxWidth).coerceAtLeast(menuMinWidth)
    fun close() { expanded = false; restoreFocus = true }
    fun open() { if (enabled) { activeValue = value; expanded = true } }
    fun pick(index: Int) {
        close()
        if (enabled && options[index].value != value) onValueChange(options[index].value)
    }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    LaunchedEffect(options.map { it.value }, activeValue) {
        if (options.none { it.value == activeValue }) activeValue = value
    }
    LaunchedEffect(expanded, restoreFocus) {
        if (!expanded && restoreFocus) {
            withFrameNanos { }
            if (enabled) anchorFocus.requestFocus()
            restoreFocus = false
        }
    }
    Box {
        PcControlLayout(
            modifier.onSizeChanged { anchorWidth = it.width }
                .focusRequester(anchorFocus)
                .onPreviewKeyEvent { event ->
                    if (enabled && (event.key == Key.DirectionDown || event.key == Key.DirectionUp)) {
                        if (event.type == KeyEventType.KeyDown) open()
                        true
                    } else false
                }
                .pcControlSurface(source, when {
                    !enabled -> colors.panel
                    pressed -> colors.pressed
                    hovered -> colors.hover
                    else -> colors.panel
                }, enabled, borderColor = colors.border)
                .clickable(interactionSource = source, indication = null, enabled = enabled,
                    role = Role.DropdownList, onClick = { if (expanded) close() else open() })
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
        ) {
            BoxWithConstraints {
                val boundedWidth = constraints.hasBoundedWidth
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                    options[selected].icon?.let { PcIcon(it) }
                    PcText(options[selected].label,
                        if (boundedWidth) Modifier.weight(1f, fill = false) else Modifier,
                        style = PcTheme.typography.control)
                    PcIcon(PcIcons.ChevronDown)
                }
            }
        }
        if (expanded && enabled) {
            Popup(popupPositionProvider = remember(gap) { PcAnchorPosition(gap) },
                onDismissRequest = { close() }, properties = PopupProperties(focusable = true)) {
                PcSurface(
                    modifier = Modifier.widthIn(min = menuMinWidth, max = menuMaxWidth)
                        .heightIn(max = minOf(320.dp, maxHeight))
                        .pcPopupAppearance()
                        .dropShadow(PcTheme.shapes.control, Shadow(radius = 18.dp, color = colors.shadow, offset = DpOffset(0.dp, 4.dp)))
                        .semantics { paneTitle = contentDescription }
                        .onPreviewKeyEvent { event ->
                            val key = event.key
                            if (key == Key.Escape) {
                                if (event.type == KeyEventType.KeyUp) close()
                                true
                            } else if (key == Key.DirectionUp || key == Key.DirectionDown || key == Key.MoveHome || key == Key.MoveEnd) {
                                if (event.type == KeyEventType.KeyDown) {
                                    val next = when (key) {
                                        Key.MoveHome -> 0
                                        Key.MoveEnd -> options.lastIndex
                                        Key.DirectionUp -> (active - 1 + options.size) % options.size
                                        else -> (active + 1) % options.size
                                    }
                                    activeValue = options[next].value
                                }
                                true
                            } else if (key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar) {
                                if (event.type == KeyEventType.KeyUp) pick(active.coerceIn(options.indices))
                                true
                            } else false
                        },
                    shape = PcTheme.shapes.control, borderColor = colors.border, borderWidth = PcTheme.strokes.border,
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup().focusGroup()) {
                        options.forEachIndexed { index, option ->
                            val rowSource = remember(option.value) { MutableInteractionSource() }
                            val rowHovered by rowSource.collectIsHoveredAsState()
                            val rowPressed by rowSource.collectIsPressedAsState()
                            PcControlLayout(
                                Modifier.fillMaxWidth().focusRequester(optionFocus[index])
                                    .onFocusChanged { if (it.isFocused) activeValue = option.value }
                                    .pcControlSurface(rowSource, when {
                                        rowPressed -> colors.pressed
                                        rowHovered || active == index -> colors.hover
                                        else -> colors.panel
                                    })
                                    .selectable(option.value == value, interactionSource = rowSource, indication = null,
                                        role = Role.RadioButton, onClick = { pick(index) }),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                                    option.icon?.let { PcIcon(it) }
                                    PcText(option.label, Modifier.weight(1f),
                                        color = if (option.value == value) colors.accent else colors.text,
                                        style = PcTheme.typography.control)
                                    if (option.value == value) PcIcon(PcIcons.Check, tint = colors.accent)
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(active, optionFocus) {
                    withFrameNanos { }
                    optionFocus[active.coerceIn(options.indices)].requestFocus()
                }
            }
        }
    }
}
