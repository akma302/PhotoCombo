package com.combo.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.combo.ImageFixtures
import com.combo.feature.library.*
import com.combo.ui.common.PcDesktopFonts
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Реальный CMP-экран без DI; density/fontScale относятся только к тестовой сцене. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class LibraryDesignSystemTest(private val width: Int, private val scale: Float, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "width={0}, density={1}, fontScale={2}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(1200, 1f, 1f),
            arrayOf(420, 1f, 1.25f),
            arrayOf(420, 1.5f, 1.25f),
        )
    }

    @Test
    fun realScreenRendersAndKeepsActionsReachableAcrossScale() = runSkikoComposeUiTest(
        Size(width * scale, 760 * scale), density = Density(scale, fontScale),
    ) {
        val fixturePhotos = (1..6).map { photoEntity("fixture$it") }
        val bitmaps = fixturePhotos.mapIndexed { index, photo ->
            photo.id to ImageFixtures.bitmap(160, 120) { canvas ->
                val paint = Paint()
                paint.color = Color(0xFF556C82 + index * 0x00070403)
                canvas.drawRect(0f, 0f, 160f, 120f, paint)
                paint.color = Color(0xFFD5CBB5)
                canvas.drawCircle(androidx.compose.ui.geometry.Offset(118f, 27f), 14f, paint)
                paint.color = Color(0xFF334C45 + index * 0x00030204)
                canvas.drawRect(0f, 69f + index * 3f, 160f, 120f, paint)
                paint.color = Color(0xFFB29276)
                canvas.drawRect(21f + index * 4f, 45f, 61f + index * 4f, 105f, paint)
            }
        }.toMap()
        var photos by mutableStateOf(fixturePhotos)
        var selected by mutableStateOf(emptyList<String>())
        var selectionMode by mutableStateOf(false)
        var message by mutableStateOf<String?>(null)
        val opened = mutableListOf<String>()
        val combos = mutableListOf<List<String>>()
        var imports = 0
        var deletes = 0
        var dismissed = 0
        setContent {
            PcDesktopTheme(motion = PcMotion(reducedMotion = true)) {
                LibraryScreenContent(
                    photos, selected, selectionMode,
                    onOpenViewer = { opened += it },
                    onEnterSelection = { selectionMode = true },
                    onExitSelection = { selectionMode = false; selected = emptyList() },
                    onToggle = { selected = if (it in selected) selected - it else selected + it },
                    onDelete = { deletes++; photos = photos.filterNot { it.id in selected }; selected = emptyList() },
                    onCreateCombo = { combos += selected; selectionMode = false; selected = emptyList() },
                    onImport = { imports++ },
                    message = message,
                    onDismissMessage = { dismissed++; message = null },
                    thumbnail = { photo, modifier -> Image(bitmaps.getValue(photo.id), null, modifier) },
                )
            }
        }
        val output = File("build/ui-previews/library").apply { mkdirs() }
        fun capture(state: String) {
            val bitmap = onNodeWithTag("library_screen").captureToImage()
            File(output, "library-$width-$scale-$fontScale-$state.png").writeBytes(ImageFixtures.pngBytes(bitmap))
        }
        onNodeWithTag(LIB_IMPORT_TAG).assertIsDisplayed()
        capture("normal")
        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "fixture2").performClick()
        assertEquals(listOf("fixture2"), opened)
        onNodeWithTag(LIB_IMPORT_TAG).requestFocus().performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, imports)
        onNodeWithTag(LIB_SELECT_TAG).performClick()
        onNodeWithText("Selected: 0").assertExists()
        onNodeWithTag(LIB_CREATE_COMBO_TAG).assertDoesNotExist()
        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "fixture3").performClick()
        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "fixture1").performClick()
        onNodeWithTag(LIB_CREATE_COMBO_TAG).assertIsDisplayed()
        onNodeWithTag(LIB_DELETE_TAG).assertIsDisplayed()
        onNodeWithTag(LIB_IMPORT_TAG).assertIsDisplayed()
        capture("selected")
        onNodeWithTag(LIB_CREATE_COMBO_TAG).performClick()
        assertEquals(listOf(listOf("fixture3", "fixture1")), combos)
        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "fixture2").performMouseInput { rightClick() }
        onNodeWithText("Selected: 1").assertExists()
        assertEquals(listOf("fixture2"), opened)
        onNodeWithTag(LIB_DELETE_TAG).performClick()
        assertEquals(1, deletes)
        onNodeWithText("Selected: 0").assertExists()
        onNodeWithTag(LIB_CANCEL_TAG).performClick()
        onNodeWithText("Library").assertExists()
        runOnIdle { message = "Imported: 6, already added: 2, failed: 1" }
        onNodeWithText(message!!).assertIsDisplayed()
        capture("message")
        onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissed)
        runOnIdle { photos = emptyList() }
        onNodeWithText("No photos yet").assertIsDisplayed()
        onNodeWithTag(LIB_IMPORT_TAG).assertIsDisplayed()
        capture("empty")
        assertTrue(selected.isEmpty())
        File(output, "font.txt").writeText(PcDesktopFonts.resolvedFamily)
    }
}
