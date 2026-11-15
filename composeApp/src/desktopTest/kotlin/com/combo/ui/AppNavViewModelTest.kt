package com.combo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
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
import com.combo.ImageFixtures
import com.combo.core.image.ImagePicker
import com.combo.core.image.PngDestination
import com.combo.core.image.PngOutput
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.di.viewModelModule
import com.combo.domain.BitmapSource
import com.combo.domain.ComboPngExporter
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoRepository
import com.combo.domain.image.ThumbnailSource
import com.combo.feature.builder.EDITOR_CANVAS_TAG
import com.combo.feature.library.LIB_CREATE_COMBO_TAG
import com.combo.feature.library.LIB_SELECT_TAG
import com.combo.feature.library.PHOTO_CELL_TAG_PREFIX
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Переходы через настоящий [AppNav]: ViewModel'и записей создаёт Koin из [viewModelModule],
 * а аргументы перехода доезжают параметром фабрики. Связку «запись навигации + фабрика»
 * проверяем на живом стеке: сбой в ней виден только при реальном создании ViewModel.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavViewModelTest : UiTestBase() {

    /** Прикладной Koin с фейковым слоем данных: фабрики ViewModel — боевые. */
    private fun appKoin(
        photoRepo: PhotoRepository,
        comboRepo: FakeComboRepository = FakeComboRepository(),
        bitmapSource: BitmapSource = StubBitmapSource(),
        settings: FakeGallerySettingsRepository = FakeGallerySettingsRepository(),
    ): KoinApplication = trackKoin(
        koinApplication {
            modules(
                viewModelModule,
                module { single<com.combo.domain.GallerySettingsRepository> { settings } },
                module {
                    single<PhotoRepository> { photoRepo }
                    single<ComboRepository> { comboRepo }
                    single<BitmapSource> { bitmapSource }
                    single {
                        val output = object : PngOutput {
                            override suspend fun chooseDestination(name: String): PngDestination? = null
                            override suspend fun write(destination: PngDestination, image: ImageBitmap) =
                                error("The test picker never returns a destination")
                        }
                        ComboPngExporter(comboRepo, photoRepo, bitmapSource, output, ioDispatcher)
                    }
                    single<ImagePicker> { FakeImagePicker() }
                    single<ThumbnailSource> { EmptyThumbnailSource }
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
    fun removingAppNavCancelsTheRootViewModelCollectors() = runComposeUiTest {
        val delegate = FakePhotoRepository()
        val collecting = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val repo = object : PhotoRepository by delegate {
            override fun all() = flow<List<PhotoEntity>> {
                collecting.set(true)
                try {
                    emit(emptyList())
                    awaitCancellation()
                } finally {
                    cancelled.set(true)
                }
            }
        }
        val koin = appKoin(repo)
        val present = mutableStateOf(true)
        setContent {
            KoinIsolatedContext(koin) {
                if (present.value) AppNav(backDispatcher)
            }
        }
        waitUntil { collecting.get() }

        runOnIdle { present.value = false }
        waitUntil { cancelled.get() }
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

    @Test
    fun editFromComboListOpensBuilder() = runComposeUiTest {
        val comboRepo = FakeComboRepository(
            listOf(ComboWithItems(combo = comboEntity("c1", "Trip"), items = emptyList())),
        )
        setAppNavContent(appKoin(FakePhotoRepository(), comboRepo))

        onNodeWithText("Combos").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("combo_card_c1").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "список комбо не предлагает неподдерживаемое действие Play",
            onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isEmpty(),
        )

        onNodeWithContentDescription("Edit").performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test
    fun previewMouseButtonsOpenViewerAndEditor() = runComposeUiTest {
        val photos = listOf(photoEntity("p1"), photoEntity("p2"))
        val combos = listOf(
            ComboWithItems(comboEntity("c1", "Trip"), listOf(comboItemEntity("i1", "c1", 0, photoId = "p1"))),
            ComboWithItems(comboEntity("c2", "Weekend"), listOf(comboItemEntity("i2", "c2", 0, photoId = "p2"))),
        )
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int) = ImageFixtures.patternBitmap()
        }
        setAppNavContent(appKoin(FakePhotoRepository(photos), FakeComboRepository(combos), bitmapSource = source))
        onNodeWithText("Combos").performClick()
        onNodeWithTag("combo_preview_c2").performMouseInput { click() }
        onNodeWithText("Weekend").assertIsDisplayed()
        val viewer = onNodeWithTag("combo_viewer")
        val fitted = viewer.captureToImage()
        capturePreview("combo-viewer", fitted)

        viewer.performTouchInput { swipeRight() }
        onNodeWithText("Trip").assertIsDisplayed()
        viewer.performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Weekend").assertIsDisplayed()

        viewer.performTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f),
                end0 = center - Offset(125f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(125f, 0f),
            )
        }
        val zoomed = viewer.captureToImage()
        val sampleX = fitted.width / 2
        val sampleY = fitted.height / 10
        assertTrue(fitted.toPixelMap()[sampleX, sampleY] != zoomed.toPixelMap()[sampleX, sampleY])
        capturePreview("combo-viewer-zoomed", zoomed)

        pressEsc()
        val preview = onNodeWithTag("combo_preview_c2").assertIsDisplayed()
        preview.performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            moveTo(topCenter - Offset(0f, 30f))
            release(MouseButton.Secondary)
        }
        preview.assertIsDisplayed()
        preview.performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithTag(EDITOR_CANVAS_TAG).assertIsDisplayed()
        onNodeWithTag("builder_combo_name").assertTextEquals("Weekend")
        pressEsc()
        preview.assertIsDisplayed()
    }

    private fun capturePreview(name: String, image: ImageBitmap) {
        File("build/ui-previews", "$name.png").apply {
            parentFile.mkdirs()
            writeBytes(ImageFixtures.pngBytes(image))
        }
    }

    @Test
    fun galleryScaleSurvivesTabsViewerAndBuilderIndependently() = runComposeUiTest {
        val settings = FakeGallerySettingsRepository()
        setAppNavContent(appKoin(FakePhotoRepository(listOf(photoEntity("p1"))), settings = settings))
        waitUntil { onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + "p1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("gallery_size_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        onNodeWithTag(PHOTO_CELL_TAG_PREFIX + "p1").performClick()
        waitForIdle()
        pressEsc()
        onNodeWithText("150%").assertIsDisplayed()
        onNodeWithText("Combos").performClick()
        onNodeWithText("100%").assertIsDisplayed()
        onNodeWithTag("gallery_size_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        onNodeWithTag("combo_list_new_combo").performClick()
        waitForIdle()
        pressEsc()
        onNodeWithTag("builder_exit_discard").performClick()
        onNodeWithText("75%").assertIsDisplayed()
        onNodeWithText("Photos").performClick()
        onNodeWithText("150%").assertIsDisplayed()
        assertEquals(com.combo.data.GallerySettings(150f, 75f), settings.settings)
    }

}
