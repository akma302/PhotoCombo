package com.combo

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.background.BackgroundSpec
import com.combo.core.image.DesktopPngOutput
import com.combo.core.image.PngDestination
import com.combo.core.image.PngOutput
import com.combo.core.image.SkiaImageCodec
import com.combo.core.platform.DesktopAppDirs
import com.combo.core.platform.DesktopLogger
import com.combo.data.ComboWithItems
import com.combo.domain.BitmapSource
import com.combo.domain.ComboPngExporter
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.imageio.ImageIO

class ComboPngExporterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val black = 0xFF000000.toInt()
    private val photos = listOf(
        photoEntity("bottom").copy(width = 120, height = 80, avgColor = red),
        photoEntity("top").copy(width = 80, height = 40, avgColor = 0xFF00FF00.toInt()),
    )
    private val snapshot = ComboWithItems(
        comboEntity("c1", "Trip", BackgroundSpec.Solid(black)).copy(canvasWidth = 80, canvasHeight = 60),
        listOf(
            comboItemEntity("top", "c1", zIndex = 1, framingMode = "FIT", photoId = "top").copy(
                centerX = 50f, centerY = 30f, width = 40f, height = 40f, contentScale = 5f, panX = 100f,
            ),
            comboItemEntity("bottom", "c1", photoId = "bottom").copy(
                centerX = 30f, centerY = 30f, width = 40f, height = 40f, contentScale = 0.5f,
            ),
        ),
    )

    @Test
    fun `exports native canvas pixels with the saved background, framing and layer order`() = runBlocking {
        val target = File(temp.root, "Trip.png")
        val requests = mutableMapOf<String, Int>()
        val exporter = exporter(target) { id, pixels ->
            requests[id] = pixels
            if (id == "top") ImageFixtures.solidBitmap(40, 20, blue)
            else ImageFixtures.solidBitmap(60, 40, red)
        }

        assertEquals("Trip.png", exporter.export("c1"))
        assertEquals(mapOf("top" to 40, "bottom" to 60), requests)
        val image = ImageIO.read(target)
        assertEquals(80, image.width)
        assertEquals(60, image.height)
        assertEquals(black, image.getRGB(3, 3))
        assertEquals(red, image.getRGB(20, 30))
        assertEquals(blue, image.getRGB(40, 30))
        assertTrue("FIT letterbox is drawn", ((image.getRGB(60, 14) ushr 8) and 0xFF) in 126..129)
    }

    @Test
    fun `cancelling the save dialog does not read the photos`() = runBlocking {
        val exporter = exporter(null) { _, _ -> error("A cancelled export must not decode") }
        assertNull(exporter.export("c1"))
        assertEquals(listOf("library"), temp.root.list()?.toList())
    }

    @Test
    fun `an unreadable photo leaves an existing destination untouched`() = runBlocking {
        val target = temp.newFile("Trip.png").apply { writeText("previous contents") }
        val exporter = exporter(target) { _, _ -> error("Photo file is unreadable") }

        val result = runCatching { exporter.export("c1") }
        assertEquals("Photo file is unreadable", result.exceptionOrNull()?.message)
        assertEquals("previous contents", target.readText())
        assertEquals(setOf("library", "Trip.png"), temp.root.list()?.toSet())
    }

    private fun exporter(target: File?, load: (String, Int) -> ImageBitmap): ComboPngExporter {
        val appDirs = DesktopAppDirs(temp.newFolder("library").toOkioPath())
        val desktop = DesktopPngOutput(SkiaImageCodec(FileSystem.SYSTEM, DesktopLogger()), appDirs)
        val output = object : PngOutput by desktop {
            override suspend fun chooseDestination(name: String): PngDestination? {
                assertEquals("Trip", name)
                return target?.let { PngDestination(it.toOkioPath(), replaceExisting = it.exists()) }
            }
        }
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap = load(photoId, requiredPx)
        }
        return ComboPngExporter(
            FakeComboRepository(listOf(snapshot)), FakePhotoRepository(photos), source, output, Dispatchers.IO,
        )
    }
}
