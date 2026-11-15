package com.combo.ui

import androidx.compose.ui.graphics.ImageBitmap
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
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.combo.data.createAppDb
import com.combo.domain.ComboRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * Переходы через настоящий [AppNav]: ViewModel'и записей создаёт Koin из [viewModelModule],
 * а аргументы перехода доезжают параметром фабрики. Связку «запись навигации + фабрика»
 * проверяем на живом стеке: сбой в ней виден только при реальном создании ViewModel.
 */
@OptIn(ExperimentalTestApi::class)
class AppNavViewModelTest : UiTestBase() {

    @get:Rule
    val tempDir = TemporaryFolder()

    /** Прикладной Koin с фейковым слоем данных: фабрики ViewModel — боевые. */
    private fun appKoin(
        photoRepo: PhotoRepository,
        comboRepo: ComboRepository = FakeComboRepository(),
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
                    single<IdGenerator> {
                        var nextId = 0
                        IdGenerator { "gen-${nextId++}" }
                    }
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

    /** Из списка комбо ведёт один путь — в редактор; кнопки Play больше нет. */
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

    @Test
    fun savingEditedComboAsNewRetainsOriginalAndCreatesIndependentFrames() {
        val db = createAppDb(tempDir.newFile("combos.db"))
        try {
            val photos = listOf(photoEntity("p1"), photoEntity("p2"))
            val initial = ComboWithItems(
                comboEntity("c1", "Trip").copy(createdAt = 100L),
                listOf(
                    comboItemEntity("i1", "c1", 0, photoId = "p1"),
                    comboItemEntity("i2", "c1", 1, photoId = "p2"),
                ),
            )
            val repo = ComboRepositoryImpl(db, db.combos())
            runBlocking {
                for (photo in photos) db.photos().insert(photo)
                repo.saveCombo(initial.combo, initial.items)
            }

            runSkikoComposeUiTest(Size(630f, 1050f), density = Density(1.5f, 1.25f)) {
                setAppNavContent(appKoin(FakePhotoRepository(photos), repo))
                onNodeWithText("Combos").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("combo_edit_c1").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("combo_edit_c1").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("builder_combo_name").performTextReplacement("Trip variation")
                onNodeWithText("Save as new").performClick()

                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("combo_preview_gen-0").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("combo_preview_c1").assertIsDisplayed()
            }

            runBlocking {
                val (combo, frames) = checkNotNull(repo.get("gen-0"))
                assertEquals("Trip variation", combo.name)
                assertEquals(1_000L, combo.createdAt)
                assertEquals(combo.createdAt, combo.updatedAt)
                assertEquals(listOf("p1", "p2"), frames.map { it.photoId })
                assertEquals(listOf(0, 1), frames.map { it.zIndex })
                assertEquals(setOf("gen-1", "gen-2"), frames.map { it.id }.toSet())
                assertTrue(frames.all { it.comboId == combo.id })
                assertEquals(initial, repo.get("c1"))
            }
        } finally {
            db.close()
        }
    }
}
