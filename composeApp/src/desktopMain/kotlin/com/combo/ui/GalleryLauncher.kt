package com.combo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.combo.ui.common.GalleryGrid
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.gallery.DesignSystemGallery
import com.combo.ui.gallery.GalleryExample

/** Launches the real component gallery with synthetic shared-UI examples. */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PhotoCombo · UI components",
        state = rememberWindowState(width = 960.dp, height = 960.dp)) {
        PcDesktopTheme {
            DesignSystemGallery(sharedGalleryExamples())
        }
    }
}

/** Examples outside the design-system module use deterministic, in-memory data. */
fun sharedGalleryExamples(): List<GalleryExample> = listOf(GalleryExample("Gallery grid") {
    GalleryGrid((1..12).toList(), { it }, 120.dp, Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), horizontalGap = 8.dp) { item, _ ->
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(
            listOf(Color(0xff657d8a), Color(0xffb3997d), Color(0xff65837b))[(item - 1) % 3])
            .testTag("gallery_grid_item_$item"))
    }
})
