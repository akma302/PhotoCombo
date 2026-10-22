package com.combo.ui.common.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.combo.ui.common.designsystem.theme.PcTheme

/** Desktop backend позволяет задать scrim и отключить движение по настройке темы. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PcPlatformDialog(onDismissRequest: () -> Unit, dismissOnClickOutside: Boolean, content: @Composable () -> Unit) {
    Dialog(onDismissRequest, properties = DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = false,
        scrimColor = PcTheme.colors.scrim,
        animateTransition = false,
    ), content = content)
}
