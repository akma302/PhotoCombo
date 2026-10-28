package com.combo.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GalleryPreviewsTest {
    @Test fun previewsRenderWithoutPersonalData() = runSkikoComposeUiTest(Size(600f, 600f)) {
        var index by mutableIntStateOf(0)
        setContent {
            when (index) {
                0 -> GalleryGridPreview()
                1 -> PhotoGridPreview()
                2 -> GallerySizePreview()
                3 -> BackNavPreview()
                else -> ThumbnailPreview()
            }
        }
        onNodeWithTag("sample_2").assertExists()
        runOnIdle { index = 1 }
        onNodeWithTag("2").assertExists()
        onNodeWithContentDescription("Selected").assertExists()
        runOnIdle { index = 2 }
        onNodeWithText("Item size").assertExists()
        runOnIdle { index = 3 }
        onNodeWithContentDescription("Back").assertExists()
        runOnIdle { index = 4 }
        waitForIdle()
    }
}
