package com.combo.ui.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class ComponentPreviewsTest {
    @Test fun representativePreviewsRenderActualComponents() = runSkikoComposeUiTest(Size(600f, 600f)) {
        var preview by mutableIntStateOf(0)
        setContent { Box(Modifier.fillMaxSize().testTag("preview")) {
            when (preview) {
                0 -> ButtonPreview()
                1 -> CheckboxPreview()
                2 -> TextFieldPreview()
                3 -> SliderPreview()
                4 -> SegmentedPreview()
                5 -> SelectPreview()
                6 -> ActionGroupPreview()
                7 -> TooltipPreview()
                8 -> TextPreview()
                9 -> SurfacePreview()
                10 -> DividerPreview()
                11 -> IconPreview()
                12 -> IconButtonPreview()
                else -> ControlLayoutPreview()
            }
        } }
        val output = File("build/ui-previews/component-previews").apply { mkdirs() }
        for (index in 0..13) {
            runOnIdle { preview = index }
            waitForIdle()
            val bitmap = onNodeWithTag("preview").captureToImage()
            Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> File(output, "component-$index.png").writeBytes(data.bytes) }
            }
            when (index) {
                0 -> onNodeWithText("Primary").assertExists()
                1 -> onNodeWithText("Selected").assertExists()
                2 -> onNodeWithContentDescription("Required").assertExists()
                3 -> onNodeWithContentDescription("Item size").assertExists()
                4 -> onNodeWithText("Frame").assertExists()
                5 -> onNodeWithContentDescription("Aspect").assertExists()
                6 -> onNodeWithText("Fit").assertExists()
                7 -> onNodeWithText("Hover or focus").assertExists()
                8 -> onNodeWithText("Title").assertExists()
                9 -> onNodeWithText("Panel").assertExists()
                10 -> onNodeWithText("Below").assertExists()
                11 -> onNodeWithTag("preview").assertExists()
                12 -> onNodeWithContentDescription("Add").assertExists()
                else -> onNodeWithText("Control").assertExists()
            }
        }
    }
    @Test fun dialogPreviewRendersItsContent() = runSkikoComposeUiTest(Size(600f, 600f)) {
        setContent { DialogPreview() }
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Save this combo?").assertExists()
    }

}
