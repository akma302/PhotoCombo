package com.combo.ui

import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import java.awt.Frame

/** Optional JBR integration; unsupported runtimes retain their normal window decorations. */
internal class JbrTitleBar private constructor(private val titleBar: WindowDecorations.CustomTitleBar) {
    fun setClientArea(client: Boolean) = titleBar.forceHitTest(client)

    companion object {
        fun install(frame: Frame): JbrTitleBar? {
            if (!System.getProperty("os.name").startsWith("Windows")) return null
            val decorations = JBR.getWindowDecorations() ?: return null
            val titleBar = decorations.createCustomTitleBar().apply {
                height = 40f
                putProperty("controls.visible", false)
            }
            decorations.setCustomTitleBar(frame, titleBar)
            return JbrTitleBar(titleBar)
        }
    }
}
