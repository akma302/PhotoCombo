package com.combo.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.gallery.DesignSystemGallery
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedGalleryTest {
    @Test fun selectsSharedGalleryGridWithoutApplicationData() = runSkikoComposeUiTest(Size(800f, 700f)) {
        setContent { PcDesktopTheme { DesignSystemGallery(sharedGalleryExamples()) } }
        onNodeWithContentDescription("Gallery component").performClick()
        onNodeWithText("Gallery grid").performScrollTo().performClick()
        onNodeWithTag("gallery_grid_item_1").assertIsDisplayed()
        onNodeWithTag("gallery_primary").assertDoesNotExist()
    }
}
