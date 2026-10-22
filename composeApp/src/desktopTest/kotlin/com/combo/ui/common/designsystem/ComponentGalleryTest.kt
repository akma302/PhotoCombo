package com.combo.ui.common.designsystem

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.combo.ui.common.PcDesktopFonts
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ui.gallery.DesignSystemGallery
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Снимки настоящего UI: scale моделирует density сцены, а не настройку всей Windows. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class ComponentGalleryTest(private val width: Int, private val scale: Float, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "width={0}, density={1}, fontScale={2}")
        fun cases(): List<Array<Any>> = listOf(420, 800, 1200).flatMap { width ->
            listOf(1f, 1.5f).flatMap { scale -> listOf(1f, 1.25f).map { font -> arrayOf<Any>(width, scale, font) } }
        }
    }

    @Test
    fun galleryRemainsInteractiveAcrossWidthsDensityAndFontScale() = runSkikoComposeUiTest(
        Size(width * scale, (if (width == 1200) 760 else 700) * scale), density = Density(scale, fontScale),
    ) {
        setContent { PcDesktopTheme(motion = PcMotion(reducedMotion = true)) { DesignSystemGallery() } }
        val prefix = "gallery-${width}-${scale}-${fontScale}"
        fun capture(suffix: String) { save(onNodeWithTag("pc_gallery").captureToImage(), "$prefix-$suffix") }
        capture("top")
        val save = onNodeWithTag("gallery_primary")
        save.performScrollTo().requestFocus()
        capture("focus")
        save.performMouseInput { moveTo(center) }
        capture("hover")
        save.performMouseInput { press() }
        capture("pressed")
        save.performMouseInput { release() }
        onNodeWithText("Always fit").performScrollTo().performClick().assertIsOff()
        onNodeWithContentDescription("Name").performScrollTo().performTextReplacement("Gallery test with spaces")
        onNodeWithContentDescription("Name").assertTextContains("Gallery test with spaces")
        onNodeWithText("Open dialog").performScrollTo()
        capture("bottom")
        val requiredName = onNodeWithContentDescription("Required name")
        requiredName.performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a name"))
            .performTextReplacement("Gallery name")
        requiredName.assertTextContains("Gallery name")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        requiredName.performTextReplacement(" ")
        requiredName.assertTextContains(" ")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a name"))
        requiredName.performTextReplacement("")
        onNodeWithText("Open dialog").performScrollTo()
        onNodeWithContentDescription("Canvas aspect").performClick()
        val menu = onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Canvas aspect"))
        save(menu.captureToImage(), "$prefix-menu")
        onNodeWithText("4:3").performClick()
        onNodeWithContentDescription("Canvas aspect").assertTextContains("4:3")
        onNodeWithText("Open dialog").performClick()
        val dialog = onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Save composition?"))
        save(dialog.captureToImage(), "$prefix-dialog")
        onNodeWithText("Cancel").performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("Save composition?").assertDoesNotExist()
        File("build/ui-previews/design-system/font.txt").apply { parentFile.mkdirs(); writeText(PcDesktopFonts.resolvedFamily) }
    }

    private fun save(bitmap: ImageBitmap, name: String) {
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
        }
        File("build/ui-previews/design-system/$name.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
    }
}
