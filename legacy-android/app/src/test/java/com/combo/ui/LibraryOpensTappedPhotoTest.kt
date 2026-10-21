package com.combo.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.feature.library.LibraryScreen
import com.combo.feature.library.LibraryViewModel
import com.combo.feature.library.PHOTO_CELL_TAG_PREFIX
import com.combo.feature.viewer.PhotoViewerScreen
import com.combo.feature.viewer.PhotoViewerViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Баг 1: в Library тап по любому фото открывал полноэкранный viewer на ПЕРВОМ фото списка.
 *
 * Проверяем весь путь целиком: тап по третьей ячейке сетки → photoId, ушедший в навигацию →
 * заголовок (т.е. текущая страница pager'а) полноэкранного viewer'а.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryOpensTappedPhotoTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingAnyPhotoOpensViewerOnThatPhoto() {
        val photos = listOf(
            photoEntity("p1", "first.jpg"),
            photoEntity("p2", "second.jpg"),
            photoEntity("p3", "third.jpg"),
        )
        // Сетка библиотеки уже знает про фото, а viewer получает список из БД отдельным
        // запросом — то есть ПОСЛЕ того, как он уже скомпонован. Именно эта
        // асинхронность (в проде её даёт Room Flow) ломала initialPage у pager'а.
        val libraryRepo = FakePhotoRepository(photos)
        val viewerRepo = FakePhotoRepository()
        val libraryVm = LibraryViewModel(libraryRepo)
        val viewerVm = PhotoViewerViewModel(SavedStateHandle(), viewerRepo, FakeBitmapSource())

        val openedPhotoId = mutableStateOf<String?>(null)
        val viewerOpen = mutableStateOf(false)

        compose.setContent {
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

        val thirdCell = compose.onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p3")
        compose.waitUntil(timeoutMillis = 5_000) { thirdCell.fetchSemanticsNodes().isNotEmpty() }

        thirdCell[0].performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewerOpen.value }
        // Ждём, пока просмотр реально откомпоновался (в топбаре есть кнопка «Back»):
        // до этого момента его список фото пуст, как в приложении до ответа Room.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }

        // Первый кадр viewer'а отрисован со пустым списком — «ответ БД» приходит позже.
        viewerRepo.push(photos)
        compose.waitForIdle()

        assertEquals("p3", openedPhotoId.value)

        // Список размонтирован: viewer открыт ровно на третьем фото, а не на первом.
        compose.onNodeWithText("third.jpg").assertIsDisplayed()
        compose.onNodeWithText("first.jpg").assertDoesNotExist()
    }
}
