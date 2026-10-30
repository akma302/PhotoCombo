package com.combo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.common.designsystem.primitives.PcText

/** Standalone window for testing native window behavior without opening a library. */
fun main() = application {
    val state = rememberWindowState(width = 480.dp, height = 700.dp)
    var windowVisible by remember { mutableStateOf(false) }
    Window(onCloseRequest = ::exitApplication, visible = windowVisible, title = "PhotoCombo", state = state) {
        var titleBar by remember(window) { mutableStateOf<JbrTitleBar?>(null) }
        LaunchedEffect(window) { titleBar = JbrTitleBar.install(window); windowVisible = true }
        PcDesktopTheme {
            Column {
                titleBar?.let { bar ->
                    WindowTitleBar(
                        maximized = state.placement == WindowPlacement.Maximized,
                        onMinimize = { state.isMinimized = true },
                        onToggleMaximize = {
                            state.placement = if (state.placement == WindowPlacement.Maximized)
                                WindowPlacement.Floating else WindowPlacement.Maximized
                        },
                        onClose = ::exitApplication,
                        onHitTest = bar::setClientArea,
                    )
                }
                PcText(if (titleBar == null) "Standard window: JBR title bar unavailable" else "Title bar preview")
            }
        }
    }
}
