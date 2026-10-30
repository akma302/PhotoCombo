package com.combo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

/** Window actions are supplied by the platform window host. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WindowTitleBar(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    onHitTest: (Boolean) -> Unit = {},
) {
    Row(modifier.fillMaxWidth().height(40.dp).background(PcTheme.colors.app), verticalAlignment = Alignment.CenterVertically) {
        Box(titleModifier.hitTestArea(false, onHitTest).weight(1f).fillMaxHeight().padding(start = 14.dp), contentAlignment = Alignment.CenterStart) {
            PcText("PhotoCombo", maxLines = 1)
        }
        WindowAction(WindowIcons.Minimize, "Minimize", onMinimize, onHitTest = onHitTest)
        WindowAction(if (maximized) WindowIcons.Restore else WindowIcons.Maximize,
            if (maximized) "Restore" else "Maximize", onToggleMaximize, onHitTest = onHitTest)
        WindowAction(WindowIcons.Close, "Close", onClose, danger = true, onHitTest = onHitTest)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowAction(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false, onHitTest: (Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val fill = when {
        danger && (hovered || pressed) -> PcTheme.colors.dangerFill
        pressed -> PcTheme.colors.pressed
        hovered -> PcTheme.colors.hover
        else -> Color.Transparent
    }
    Box(Modifier.hitTestArea(true, onHitTest).width(46.dp).fillMaxHeight().pcControlSurface(source, fill)
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        PcIcon(icon, size = 16.dp)
    }
}

/** All window glyphs share the same viewport and are centered independently of text fonts. */
private object WindowIcons {
    private fun vector(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 16.dp, 16.dp, 16f, 16f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1f, strokeLineCap = StrokeCap.Square, pathBuilder = draw)
        }.build()

    val Minimize = vector("WindowMinimize") { moveTo(3f, 8f); lineTo(13f, 8f) }
    val Maximize = vector("WindowMaximize") { moveTo(3f, 3f); lineTo(13f, 3f); lineTo(13f, 13f); lineTo(3f, 13f); close() }
    val Restore = vector("WindowRestore") {
        moveTo(5f, 3f); lineTo(13f, 3f); lineTo(13f, 11f); moveTo(3f, 5f)
        lineTo(11f, 5f); lineTo(11f, 13f); lineTo(3f, 13f); close()
    }
    val Close = vector("WindowClose") { moveTo(3f, 3f); lineTo(13f, 13f); moveTo(13f, 3f); lineTo(3f, 13f) }
}

// JBR resets hit-test selection for each mouse event, so update it for every
// relevant event rather than only when the pointer first enters the area.
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.hitTestArea(client: Boolean, onHitTest: (Boolean) -> Unit): Modifier =
    onPointerEvent(PointerEventType.Enter) { onHitTest(client) }
        .onPointerEvent(PointerEventType.Move) { onHitTest(client) }
        .onPointerEvent(PointerEventType.Press) { onHitTest(client) }
        .onPointerEvent(PointerEventType.Release) { onHitTest(client) }
