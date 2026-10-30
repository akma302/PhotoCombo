package com.combo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.combo.ui.common.PcDesktopTheme
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

private interface WindowProc : StdCallLibrary.StdCallCallback {
    fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
}

private interface NativeWindows : StdCallLibrary {
    fun CallWindowProcW(old: Pointer, hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun IsZoomed(hwnd: HWND): Boolean
}

/** Experimental only: HWND subclassing is scoped to the disposable demonstration window. */
private class Win32Chrome(private val frame: Frame) {
    private val user = User32.INSTANCE
    private val api = Native.load("user32", NativeWindows::class.java)
    private val hwnd = HWND(Native.getComponentPointer(frame))
    private var oldProc: Pointer? = null
    private val proc = object : WindowProc {
        override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
            val old = oldProc ?: return user.DefWindowProc(hwnd, msg, wParam, lParam)
            if (msg == 0x0083 && wParam.toLong() != 0L) { // WM_NCCALCSIZE
                if (api.IsZoomed(hwnd)) {
                    val monitor = user.MonitorFromWindow(hwnd, 2)
                    val info = MONITORINFO()
                    if (user.GetMonitorInfo(monitor, info).booleanValue()) {
                        // NCCALCSIZE_PARAMS starts with the proposed client RECT.
                        val rect = Pointer(lParam.toLong())
                        rect.setInt(0, info.rcWork.left)
                        rect.setInt(4, info.rcWork.top)
                        rect.setInt(8, info.rcWork.right)
                        rect.setInt(12, info.rcWork.bottom)
                    }
                }
                return LRESULT(0)
            }
            if (msg == 0x0084) { // WM_NCHITTEST; coordinates are signed screen pixels.
                val rect = RECT()
                if (user.GetWindowRect(hwnd, rect)) {
                    val packed = lParam.toLong()
                    val x = packed.toInt().toShort().toInt() - rect.left
                    val y = (packed shr 16).toInt().toShort().toInt() - rect.top
                    val scale = frame.graphicsConfiguration.defaultTransform.scaleX
                    val width = rect.right - rect.left
                    val height = rect.bottom - rect.top
                    val edge = (8 * scale).roundToInt()
                    val title = (40 * scale).roundToInt()
                    val button = (46 * scale).roundToInt()
                    val maximized = frame.extendedState and Frame.MAXIMIZED_BOTH != 0
                    val hit = if (!maximized && y < edge && x < edge) 13
                    else if (!maximized && y < edge && x >= width - edge) 14
                    else if (!maximized && y >= height - edge && x < edge) 16
                    else if (!maximized && y >= height - edge && x >= width - edge) 17
                    else if (!maximized && x < edge) 10
                    else if (!maximized && x >= width - edge) 11
                    else if (!maximized && y < edge) 12
                    else if (!maximized && y >= height - edge) 15
                    else if (y < title && x >= width - button) 20 // HTCLOSE
                    else if (y < title && x >= width - button * 2) 9 // HTMAXBUTTON: Snap Layouts
                    else if (y < title && x >= width - button * 3) 8 // HTMINBUTTON
                    else if (y < title) 2 // HTCAPTION
                    else 1 // HTCLIENT
                    return LRESULT(hit.toLong())
                }
            }
            return api.CallWindowProcW(old, hwnd, msg, wParam, lParam)
        }
    }

    fun install() {
        val style = user.GetWindowLong(hwnd, -16)
        user.SetWindowLong(hwnd, -16, style or 0x00040000 or 0x00020000 or 0x00010000 or 0x00080000)
        oldProc = user.SetWindowLongPtr(hwnd, -4, CallbackReference.getFunctionPointer(proc))
        check(oldProc != null) { "SetWindowLongPtr failed: ${Native.getLastError()}" }
        user.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0027) // FRAMECHANGED, no move/size/z-order
    }

    fun dispose() {
        oldProc?.let { user.SetWindowLongPtr(hwnd, -4, it) }
        oldProc = null
    }
}

fun main() = application {
    val state = rememberWindowState(width = 640.dp, height = 500.dp)
    Window(onCloseRequest = ::exitApplication, title = "PhotoCombo Win32 experiment", state = state, undecorated = true) {
        DisposableEffect(window) {
            var chrome: Win32Chrome? = null
            var disposed = false
            SwingUtilities.invokeLater {
                if (!disposed) {
                    chrome = Win32Chrome(window).also { it.install() }
                }
            }
            onDispose { disposed = true; chrome?.dispose() }
        }
        MaterialTheme {
            PcDesktopTheme {
                Column(Modifier.fillMaxSize()) {
                    WindowTitleBar(
                        maximized = state.placement == androidx.compose.ui.window.WindowPlacement.Maximized,
                        onMinimize = { state.isMinimized = true },
                        onToggleMaximize = { state.placement = if (state.placement == androidx.compose.ui.window.WindowPlacement.Maximized) androidx.compose.ui.window.WindowPlacement.Floating else androidx.compose.ui.window.WindowPlacement.Maximized },
                        onClose = ::exitApplication,
                    )
                    Text("Win32 experiment — synthetic content only")
                }
            }
        }
    }
}
