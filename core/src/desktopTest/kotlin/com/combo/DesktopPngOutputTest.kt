package com.combo

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.image.DesktopPngOutput
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.image.PngDestination
import com.combo.core.image.SkiaImageCodec
import com.combo.core.image.suggestedPngName
import com.combo.core.platform.DesktopAppDirs
import com.combo.core.platform.DesktopLogger
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.imageio.ImageIO

class DesktopPngOutputTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val codec = SkiaImageCodec(FileSystem.SYSTEM, DesktopLogger())
    private val image = ImageFixtures.solidBitmap(6, 4, 0xFFFFFFFF.toInt())

    @Test
    fun `filename suggestions are valid and keep the PNG extension once`() {
        assertEquals("Trip_ summer.PNG", suggestedPngName("Trip: summer.PNG"))
        assertEquals("_CON.photo.png", suggestedPngName("CON.photo"))
        assertEquals("Combo.png", suggestedPngName(" ... "))
    }

    @Test
    fun `only a confirmed replacement changes an existing destination`() = runBlocking {
        val target = temp.newFile("combo.png").apply { writeText("existing file") }
        val output = output()
        val destination = PngDestination(target.toOkioPath(), replaceExisting = false)

        assertTrue(runCatching { output.write(destination, image) }.isFailure)
        assertEquals("existing file", target.readText())
        output.write(destination.copy(replaceExisting = true), image)

        assertEquals(6, ImageIO.read(target).width)
        assertEquals(setOf("library", "combo.png"), temp.root.list()?.toSet())
    }

    @Test
    fun `partial encoding is cleaned up without replacing the destination`() = runBlocking {
        val target = temp.newFile("combo.png").apply { writeText("existing file") }
        val brokenCodec = object : ImageCodec by codec {
            override fun encode(path: Path, image: ImageBitmap, format: ImageFormat, quality: Int): Boolean {
                path.toFile().writeText("partial PNG")
                return false
            }
        }

        val result = runCatching { output(brokenCodec).write(PngDestination(target.toOkioPath(), true), image) }
        assertTrue(result.isFailure)
        assertEquals("existing file", target.readText())
        assertEquals(setOf("library", "combo.png"), temp.root.list()?.toSet())
    }

    @Test
    fun `the library is protected from accidental replacement`() = runBlocking {
        val output = output()
        val target = temp.root.resolve("library/photo.png").apply { writeText("original photo") }
        val result = runCatching { output.write(PngDestination(target.toOkioPath(), true), image) }

        assertTrue(result.isFailure)
        assertEquals("original photo", target.readText())
    }

    private fun output(codec: ImageCodec = this.codec): DesktopPngOutput =
        DesktopPngOutput(codec, DesktopAppDirs(temp.newFolder("library").toOkioPath()))
}
