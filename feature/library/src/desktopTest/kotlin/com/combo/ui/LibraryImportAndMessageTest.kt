package com.combo.ui

import androidx.compose.ui.test.*
import com.combo.core.image.ImagePicker
import com.combo.core.image.ImageSource
import com.combo.domain.DeleteResult
import com.combo.domain.ImportResult
import com.combo.domain.PhotoRepository
import com.combo.feature.library.*
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Проверяет соединение Pc-команд с настоящим ViewModel и очередь сообщений. */
@OptIn(ExperimentalTestApi::class)
class LibraryImportAndMessageTest : UiTestBase() {
    @Test
    fun importCallsPickerAndRepositoryOnceAndShowsDismissibleSummary() = runComposeUiTest {
        var pickerCalls = 0
        var importCalls = 0
        val source = object : ImageSource {
            override val name = "fixture.jpg"
            override val mimeType = "image/jpeg"
            override fun openSource() = Buffer()
        }
        val base = FakePhotoRepository()
        val repo = object : PhotoRepository by base {
            override suspend fun importPhotos(sources: List<ImageSource>): ImportResult {
                importCalls++
                assertEquals(listOf(source), sources)
                return ImportResult(added = 1, duplicates = 2, failed = 3)
            }
        }
        val picker = object : ImagePicker {
            override suspend fun pickImages(): List<ImageSource> { pickerCalls++; return listOf(source) }
        }
        val vm = LibraryViewModel(repo, picker, settingsRepository = com.combo.ui.FakeGallerySettingsRepository())
        val koin = trackKoin(repo)
        setContent { TestScreen(koin, backDispatcher) { LibraryScreen({}, {}, vm) } }
        onNodeWithTag(LIB_IMPORT_TAG).performClick()
        waitForIdle()
        assertEquals(1, pickerCalls)
        assertEquals(1, importCalls)
        onNodeWithText("Imported: 1, already added: 2, failed: 3").assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()
        onNodeWithText("Imported: 1, already added: 2, failed: 3").assertDoesNotExist()
    }

    @Test
    fun blockedDeletionKeepsPhotoAndSelectionModeAndShowsReason() = runComposeUiTest {
        val base = FakePhotoRepository(listOf(photoEntity("used")))
        var deleteCalls = 0
        val repo = object : PhotoRepository by base {
            override suspend fun delete(id: String): DeleteResult {
                deleteCalls++
                assertEquals("used", id)
                return DeleteResult.BLOCKED_IF_USED_IN_COMBO
            }
        }
        val vm = LibraryViewModel(repo, FakeImagePicker(), settingsRepository = com.combo.ui.FakeGallerySettingsRepository())
        val koin = trackKoin(repo)
        setContent { TestScreen(koin, backDispatcher) { LibraryScreen({}, {}, vm) } }
        waitUntil { onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "used").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "used").performMouseInput { rightClick() }
        onNodeWithTag(LIB_DELETE_TAG).performClick()
        assertEquals(1, deleteCalls)
        assertEquals(listOf("used"), base.state.value.map { it.id })
        onNodeWithText("Selected: 0").assertExists()
        onNodeWithText("Photo is used in a combo and cannot be deleted").assertIsDisplayed()
        pressEsc()
        onNodeWithText("Library").assertExists()
    }
}
