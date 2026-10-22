package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme

/** Платформенный модальный слой удерживает ввод; наружу передаются только слоты и состояние. */
@Composable
fun PcDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    returnFocus: FocusRequester? = null,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    PcPlatformDialog(onDismissRequest, dismissOnClickOutside) {
        PcSurface(
            modifier = modifier.widthIn(max = 480.dp).padding(PcTheme.spacing.xl)
                .focusRequester(initialFocus).focusGroup()
                .semantics { paneTitle = title }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape) {
                        if (event.type == KeyEventType.KeyUp) onDismissRequest()
                        true
                    } else false
                },
            shape = PcTheme.shapes.dialog,
            borderColor = PcTheme.colors.border,
            borderWidth = PcTheme.strokes.border,
        ) {
            Column(Modifier.padding(PcTheme.spacing.xl)) {
                PcText(title, style = PcTheme.typography.title)
                Spacer(Modifier.height(PcTheme.spacing.lg))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(PcTheme.spacing.xl))
                PcActionGroup { actions() }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            initialFocus.requestFocus()
        }
    }
    DisposableEffect(returnFocus) {
        onDispose { returnFocus?.requestFocus() }
    }
}

/** Адаптер платформенного диалога отделяет свойства backend от общего layout. */
@Composable
internal expect fun PcPlatformDialog(onDismissRequest: () -> Unit, dismissOnClickOutside: Boolean, content: @Composable () -> Unit)
