package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcColors
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.math.roundToInt

/** Реальный CMP на panel-хосте проверяет видимый трек и центровку текста с иконкой и без неё. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class PcSegmentedTrackTest(private val scale: Float, private val direction: LayoutDirection, private val selected: Int) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "density={0}, direction={1}, selected={2}")
        fun cases(): List<Array<Any>> = listOf(1f, 1.5f).flatMap { scale ->
            LayoutDirection.entries.flatMap { direction -> (1..2).map { arrayOf<Any>(scale, direction, it) } }
        }
    }

    @Test
    fun `bordered track separates inset pill from panel and centers each content group`() = runSkikoComposeUiTest(
        Size(360f * scale, 100f * scale), density = Density(scale),
    ) {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                PcTheme {
                    Box(Modifier.testTag("scene").background(PcTheme.colors.panel).padding(12.dp)) {
                        PcSegmentedControl(
                            listOf(PcOption(1, "Photo", PcIcons.Frame), PcOption(2, "Combo")), selected, {},
                            Modifier.testTag("track").width(300.dp),
                            optionModifier = { Modifier.testTag("option-$it") },
                        )
                    }
                }
            }
        }
        val scene = onNodeWithTag("scene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val track = onNodeWithTag("track").fetchSemanticsNode().boundsInRoot
        val bitmap = scene.captureToImage()
        val pixels = bitmap.toPixelMap()
        fun color(x: Float, y: Float) = pixels[(x - sceneBounds.left).roundToInt(), (y - sceneBounds.top).roundToInt()].toArgb()
        val inset = (3f * scale).roundToInt().toFloat()
        assertEquals(34f * scale + inset * 2, track.height, 0.01f)
        assertEquals(PcColors.Dark.border.toArgb(), color(track.center.x, track.top))
        for ((id, label) in listOf(1 to "Photo", 2 to "Combo")) {
            val option = onNodeWithTag("option-$id").fetchSemanticsNode().boundsInRoot
            val text = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(inset, option.top - track.top, 0.01f)
            assertEquals(inset, track.bottom - option.bottom, 0.01f)
            assertTrue(option.left >= track.left + inset && option.right <= track.right - inset)
            assertTrue(option.width >= 34f * scale && option.height >= 34f * scale)
            val iconOffset = if (id == 1) 12f * scale * (if (direction == LayoutDirection.Ltr) 1 else -1) else 0f
            assertEquals(option.center.x + iconOffset, text.center.x, 1f)
            assertEquals(option.center.y, text.center.y, 1f)
            assertEquals(PcColors.Dark.app.toArgb(), color(option.center.x, option.top - 1f))
            assertEquals(PcColors.Dark.app.toArgb(), color(option.center.x, option.bottom + 1f))
            assertEquals(
                (if (id == selected) PcColors.Dark.panel else PcColors.Dark.app).toArgb(),
                color(option.center.x, option.top + 2f * scale),
            )
        }
        assertEquals(PcColors.Dark.app.toArgb(), color(track.left + 2f * scale, track.center.y))
        assertEquals(PcColors.Dark.app.toArgb(), color(track.right - 2f * scale, track.center.y))
        assertEquals(PcColors.Dark.panel.toArgb(), color(track.left - 2f * scale, track.center.y))
        assertEquals(PcColors.Dark.panel.toArgb(), color(track.right + 2f * scale, track.center.y))
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
        }
        File("build/ui-previews/segmented/track-$scale-$direction-$selected.png").apply {
            parentFile.mkdirs()
            writeBytes(bytes)
        }
    }
}
