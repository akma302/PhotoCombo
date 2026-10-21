package com.combo.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImagePicker
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.di.viewModelModule
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoRepository
import com.combo.feature.builder.EDITOR_CANVAS_TAG
import com.combo.feature.library.LIB_CREATE_COMBO_TAG
import com.combo.feature.library.LIB_SELECT_TAG
import com.combo.feature.library.PHOTO_CELL_TAG_PREFIX
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Переходы через настоящий [AppNav]: ViewModel'и записей создаёт Koin из [viewModelModule],
 * а аргументы перехода доезжают параметром фабрики. Связку «запись навигации + фабрика»
 * проверяем на живом стеке: сбой в ней виден только при реальном создании ViewModel.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavViewModelTest : UiTestBase() {

    /** Прикладной Koin с фейковым слоем данных: фабрики ViewModel — боевые. */
    private fun appKoin(
        photoRepo: FakePhotoRepository,
        comboRepo: FakeComboRepository = FakeComboRepository(),
        bitmapSource: BitmapSource = StubBitmapSource(),
    ): KoinApplication = trackKoin(
        koinApplication {
            modules(
                viewModelModule,
                module {
                    single<PhotoRepository> { photoRepo }
                    single<ComboRepository> { comboRepo }
                    single<BitmapSource> { bitmapSource }
                    single<ImagePicker> { FakeImagePicker() }
                    single<ImageCodec> { StubImageCodec() }
                    single<IdGenerator> { IdGenerator { "gen-1" } }
                    single<WallClock> { WallClock { 1_000L } }
                    single<CoroutineDispatcher> { ioDispatcher }
                },
            )
        },
    )

    private fun ComposeUiTest.setAppNavContent(koin: KoinApplication) {
        setContent {
            KoinIsolatedContext(koin) { AppNav(backDispatcher) }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clickingPhotoWithLibraryAlreadyLoadedOpensItsPage() = runComposeUiTest {
        // Оконный Main ставит задачи в очередь, как desktop-приложение.
        Dispatchers.setMain(Dispatchers.Swing)
        val photos = (1..6).map { photoEntity("p$it", "photo-$it.jpg") }
        val requested = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int) =
                StubBitmapSource().get(photoId, requiredPx).also { requested.add(photoId) }
        }
        setAppNavContent(appKoin(FakePhotoRepository(photos), bitmapSource = source))
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p5").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "p5").performClick()
        waitForIdle()

        onNodeWithText("photo-5.jpg").assertIsDisplayed()
        onNodeWithText("photo-1.jpg").assertDoesNotExist()
        assertTrue("выбранное фото загружено", requested.contains("p5"))
        assertFalse("просмотр не должен начинать загрузку с нулевой страницы", requested.contains("p1"))
    }

    @Test
    fun createComboFromSelectionOpensBuilder() = runComposeUiTest {
        val photoRepo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        setAppNavContent(appKoin(photoRepo))
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p1").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(LIB_SELECT_TAG).performClick()
        waitForIdle()
        onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p1")[0].performClick()
        onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p2")[0].performClick()
        waitForIdle()
        onNodeWithText("Selected: 2").assertExists()

        onNodeWithTag(LIB_CREATE_COMBO_TAG).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Из списка комбо ведёт один путь — в редактор; кнопки Play больше нет. */
    @Test
    fun editFromComboListOpensBuilder() = runComposeUiTest {
        val comboRepo = FakeComboRepository(
            listOf(ComboWithItems(combo = comboEntity("c1", "Trip"), items = emptyList())),
        )
        setAppNavContent(appKoin(FakePhotoRepository(), comboRepo))

        onNodeWithText("Combos").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Trip").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "плеера нет: кнопки Play быть не должно",
            onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isEmpty(),
        )

        onNodeWithContentDescription("Edit").performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
