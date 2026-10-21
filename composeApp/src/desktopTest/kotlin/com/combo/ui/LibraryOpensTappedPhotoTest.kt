package com.combo.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import com.combo.feature.library.LibraryScreen
import com.combo.feature.library.LibraryViewModel
import com.combo.feature.library.PHOTO_CELL_TAG_PREFIX
import com.combo.feature.viewer.PhotoViewerScreen
import com.combo.feature.viewer.PhotoViewerViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тап по любому фото открывает полноэкранный просмотр ИМЕННО на нём.
 *
 * Проверяется весь путь целиком: тап по третьей ячейке сетки → photoId, ушедший в
 * навигацию → заголовок (текущая страница pager'а) полноэкранного просмотра.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryOpensTappedPhotoTest : UiTestBase() {

    @Test
    fun tappingAnyPhotoOpensViewerOnThatPhoto() = runComposeUiTest {
        val photos = listOf(
            photoEntity("p1", "first.jpg"),
            photoEntity("p2", "second.jpg"),
            photoEntity("p3", "third.jpg"),
        )
        // Сетка библиотеки уже знает про фото, а просмотр получает список из БД отдельным
        // запросом — то есть ПОСЛЕ того, как он уже скомпонован. Именно эта
        // асинхронность (в проде её даёт Room Flow) ломала initialPage у pager'а.
        val libraryRepo = FakePhotoRepository(photos)
        val viewerRepo = FakePhotoRepository()
        val libraryVm = LibraryViewModel(libraryRepo, FakeImagePicker())
        val viewerVm = PhotoViewerViewModel(SavedStateHandle(), viewerRepo, StubBitmapSource())
        val koin = trackKoin(libraryRepo)

        val openedPhotoId = mutableStateOf<String?>(null)
        val viewerOpen = mutableStateOf(false)

        setContent {
            TestScreen(koin, backDispatcher) {
                if (viewerOpen.value) {
                    PhotoViewerScreen(
                        photoId = openedPhotoId.value.orEmpty(),
                        onBack = { viewerOpen.value = false },
                        vm = viewerVm,
                    )
                } else {
                    LibraryScreen(
                        onOpenViewer = { id ->
                            openedPhotoId.value = id
                            viewerOpen.value = true
                        },
                        onCreateCombo = {},
                        vm = libraryVm,
                    )
                }
            }
        }

        val thirdCell = onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p3")
        waitUntil(timeoutMillis = 5_000) { thirdCell.fetchSemanticsNodes().isNotEmpty() }

        thirdCell[0].performClick()
        waitUntil(timeoutMillis = 5_000) { viewerOpen.value }
        // Ждём, пока просмотр реально откомпоновался (в топбаре есть кнопка «Back»):
        // до этого момента его список фото пуст, как в приложении до ответа Room.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }

        // Первый кадр просмотра отрисован с пустым списком — «ответ БД» приходит позже.
        viewerRepo.push(photos)
        waitForIdle()

        assertEquals("p3", openedPhotoId.value)

        // Список размонтирован: просмотр открыт ровно на третьем фото, а не на первом.
        onNodeWithText("third.jpg").assertIsDisplayed()
        onNodeWithText("first.jpg").assertDoesNotExist()
    }
}
