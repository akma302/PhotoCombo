package com.combo.ui.gallery

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.combo.ui.common.PcDesktopTheme
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FocusedGalleryTest {
    @Test fun selectingExamplesShowsOnlySelectedComponentAndKeepsItInteractive() = runSkikoComposeUiTest(Size(800f, 700f)) {
        setContent { PcDesktopTheme { DesignSystemGallery() } }
        onNodeWithTag("gallery_primary").assertExists()
        fun select(label: String) {
            onNodeWithContentDescription("Gallery component").performClick()
            onNodeWithText(label).performScrollTo().performClick()
        }
        select("Checkbox")
        onNodeWithTag("gallery_primary").assertDoesNotExist()
        onNodeWithText("Selected").assertIsOn().performClick().assertIsOff()
        select("Button")
        onNodeWithText("Primary").assertExists()
        onNodeWithText("Disabled").assertIsNotEnabled()
        select("Text field")
        onNodeWithContentDescription("Required name").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a name"))
        select("All")
        onNodeWithTag("gallery_primary").assertExists()
    }
}
