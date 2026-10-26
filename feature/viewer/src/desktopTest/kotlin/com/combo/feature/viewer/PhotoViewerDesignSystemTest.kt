package com.combo.feature.viewer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.photoEntity
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Проверяет настоящий просмотр без DI и личных фотографий. */
@OptIn(ExperimentalTestApi::class)
class PhotoViewerDesignSystemTest {
    @Test
    fun requestedPageKeyboardNavigationAndBackRemainAvailable() = runSkikoComposeUiTest(Size(800f, 700f)) {
        val photos = listOf(photoEntity("a", "first.jpg"), photoEntity("b", "second.jpg"))
        var back = 0
        val loaded = mutableListOf<Int>()
        setContent { PcDesktopTheme {
            PhotoViewerContent("b", PhotoViewerViewModel.State(photos), { back++ }, { loaded += it })
        } }
        onNodeWithText("second.jpg").assertExists()
        assertEquals(1, loaded.last())
        onNodeWithTag("photo_viewer").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        onNodeWithText("first.jpg").assertExists()
        assertEquals(0, loaded.last())
        onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test
    fun emptyViewerStillAllowsBack() = runSkikoComposeUiTest(Size(420f, 700f)) {
        var back = 0
        setContent { PcDesktopTheme { PhotoViewerContent("missing", PhotoViewerViewModel.State(), { back++ }, {}) } }
        onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test
    fun viewerRendersAtNarrowAndWideSizesWithLongName() {
        for ((width, density, font) in listOf(Triple(420, 1.5f, 1.25f), Triple(1200, 1f, 1f))) {
            runSkikoComposeUiTest(Size(width * density, 700 * density), density = Density(density, font)) {
                val photo = photoEntity("fixture", "A very long photograph name with spaces and details.jpg")
                val bitmap = ImageBitmap(300, 200)
                val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
                canvas.drawRect(0f, 0f, 300f, 200f, androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color(0xFF548A9C) })
                setContent { PcDesktopTheme {
                    PhotoViewerContent(photo.id, PhotoViewerViewModel.State(listOf(photo), mapOf(photo.id to bitmap)), {}, {})
                } }
                onNodeWithContentDescription("Back").assertIsDisplayed()
                onNodeWithText(photo.originalName.orEmpty()).assertIsDisplayed()
                val capture = onNodeWithTag("photo_viewer").captureToImage()
                val bytes = Image.makeFromBitmap(capture.asSkiaBitmap()).use { image ->
                    checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
                }
                File("build/ui-previews/viewer-$width-$density-$font.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
            }
        }
    }
}
