package com.combo.feature.builder

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.combo.ImageFixtures
import com.combo.core.background.BackgroundSpec
import com.combo.core.image.PngDestination
import com.combo.core.image.PngOutput
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.domain.BitmapSource
import com.combo.domain.ComboPngExporter
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** Сохранение финального холста и повторное открытие без изменения геометрии фото. */
@OptIn(ExperimentalCoroutinesApi::class)
class ComboBuilderFinalizationTest {
    private val dispatcher = StandardTestDispatcher()
    private val original = ComboWithItems(
        combo = comboEntity("c1").copy(createdAt = 100L, background = BackgroundSpec.Solid(0xFF808080.toInt())),
        items = listOf(
            comboItemEntity("left", "c1", 0).copy(
                centerX = 300f, centerY = 400f, width = 200f, height = 300f,
                contentScale = 0.7f, panX = 12f, panY = -8f,
            ),
            comboItemEntity("right", "c1", 1).copy(
                centerX = 1500f, centerY = 350f, width = 400f, height = 500f,
                framingMode = "FIT", contentScale = 0.3f, panX = -20f, panY = 10f,
            ),
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun builder(
        repo: FakeComboRepository,
        comboId: String? = "c1",
        photos: FakePhotoRepository = FakePhotoRepository(listOf(photoEntity("p1"))),
    ): ComboBuilderViewModel = ComboBuilderViewModel(
        savedStateHandle = SavedStateHandle(mapOf("comboId" to comboId, "photos" to "p1")),
        comboRepo = repo,
        photoRepo = photos,
        bitmapSource = StubBitmapSource(),
        ids = IdGenerator { "new-id" },
        clock = WallClock { 1_000L },
        ioDispatcher = dispatcher,
    ).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `save crops to the visible photos and translates the unmodified frame geometry`() {
        val repo = FakeComboRepository(listOf(original))
        val vm = builder(repo)
        val draftsBefore = vm.state.value.items

        vm.onEvent(BuilderEvent.Save)
        dispatcher.scheduler.advanceUntilIdle()

        val (combo, items) = repo.savedCombos.single()
        assertEquals(1400, combo.canvasWidth)
        assertEquals(333, combo.canvasHeight)
        assertEquals(original.combo.id, combo.id)
        assertEquals(original.combo.name, combo.name)
        assertEquals(original.combo.createdAt, combo.createdAt)
        assertEquals(original.combo.background, combo.background)
        assertEquals(1_000L, combo.updatedAt)
        assertEquals(original.items.map { it.copy(centerX = it.centerX - 200f, centerY = it.centerY - 217f) }, items)
        assertEquals(draftsBefore, vm.state.value.items)
        assertEquals(1600f, vm.state.value.canvasW, 0f)
        assertEquals(900f, vm.state.value.canvasH, 0f)
    }

    @Test
    fun `new combo exports the photo bounds inside its frame and keeps its scale`() = runBlocking {
        val photos = FakePhotoRepository(listOf(photoEntity("p1")))
        val repo = FakeComboRepository()
        val vm = builder(repo, comboId = null, photos = photos)
        val item = vm.state.value.items.single()
        vm.onEvent(BuilderEvent.ApplyPreset(item.id, FitPreset.FIT))
        vm.onEvent(BuilderEvent.ContentZoom(item.id, 0.5f, GesturePhase.CHANGE))
        val photoScale = vm.state.value.items.single().contentScale
        vm.onEvent(BuilderEvent.Save)
        dispatcher.scheduler.advanceUntilIdle()
        val (combo, items) = repo.savedCombos.single()
        assertEquals(675, combo.canvasWidth)
        assertEquals(450, combo.canvasHeight)
        assertEquals(photoScale, items.single().contentScale, 0f)
        assertEquals(1600f, vm.state.value.canvasW, 0f)
        repo.emit(listOf(ComboWithItems(combo, items)))

        val red = 0xFFFF0000.toInt()
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int) = ImageFixtures.solidBitmap(8, 8, red)
        }
        var exported: ImageBitmap? = null
        val output = object : PngOutput {
            override suspend fun chooseDestination(name: String) = PngDestination("combo.png".toPath(), false)
            override suspend fun write(destination: PngDestination, image: ImageBitmap) {
                exported = image
            }
        }
        ComboPngExporter(repo, photos, source, output, Dispatchers.Unconfined).export(combo.id)
        val image = checkNotNull(exported).asSkiaBitmap()
        assertEquals(675, image.width)
        assertEquals(450, image.height)
        assertEquals(red, image.getColor(0, 0))
        assertEquals(red, image.getColor(674, 449))
    }

    @Test
    fun `opening and saving a finalized combo keeps its dimensions and photo transforms`() {
        val repo = FakeComboRepository(listOf(original))
        builder(repo).onEvent(BuilderEvent.Save)
        dispatcher.scheduler.advanceUntilIdle()
        val (firstCombo, firstItems) = repo.savedCombos.single()
        val reopenedRepo = FakeComboRepository(listOf(ComboWithItems(firstCombo, firstItems)))

        builder(reopenedRepo).onEvent(BuilderEvent.Save)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(firstCombo, reopenedRepo.savedCombos.single().first)
        assertEquals(firstItems, reopenedRepo.savedCombos.single().second)
    }

    @Test
    fun `failed save leaves the editable canvas and undo history intact`() {
        val repo = FakeComboRepository(listOf(original)).apply { failWith = IllegalStateException("disk full") }
        val vm = builder(repo)
        vm.onEvent(BuilderEvent.SetName("Edited"))
        val before = vm.state.value.copy()
        val draftsBefore = before.items

        vm.onEvent(BuilderEvent.Save)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.saving)
        assertEquals(before, vm.state.value)
        assertEquals(draftsBefore, vm.state.value.items)
        assertEquals(emptyList<Any>(), repo.savedCombos)
    }
}
