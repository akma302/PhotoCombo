package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.SavedStateHandle
import com.combo.core.model.Direction
import com.combo.core.render.TransformMath
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.data.PhotoEntity
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.comboContentEntity
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Поведение `ComboBuilderViewModel` на крайних случаях: пустые рамки (undo, свайп),
 * выделение после redo, сохранение без фото, добавление фото кнопкой «+Photo», откат
 * сдвига при свайпе. Чистый JVM, без Robolectric и эмулятора.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComboBuilderViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    /**
     * Для кода, который уходит в реальный Dispatchers.IO: ждём условия, попутно
     * прокручивая очередь тестового диспетчера.
     */
    private fun awaitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            settle()
            Thread.sleep(5)
        }
        assertTrue("условие не выполнилось за ${timeoutMs}мс", cond())
    }

    private fun builder(
        photos: List<PhotoEntity> = emptyList(),
        comboRepo: FakeComboRepository = FakeComboRepository(),
        comboId: String? = null,
        photosArg: String? = null,
    ): ComboBuilderViewModel {
        val handle = SavedStateHandle(
            buildMap {
                comboId?.let { put("comboId", it) }
                photosArg?.let { put("photos", it) }
            },
        )
        val vm = ComboBuilderViewModel(
            handle, comboRepo, FakePhotoRepository(photos), StubBitmapSource(),
        )
        settle()
        assertTrue("билдер не загрузился", vm.state.value.loaded)
        return vm
    }

    // ---------- undo на пустой рамке ----------

    @Test
    fun `undo after aspect change on empty frame does not throw`() {
        val vm = builder()
        assertEquals(1, vm.state.value.items.size)
        assertTrue(vm.state.value.items[0].contents.isEmpty())

        vm.onEvent(BuilderEvent.SetAspect(900, 1600))
        assertTrue(vm.state.value.canUndo)

        vm.onEvent(BuilderEvent.Undo) // было: coerceIn(0, -1) -> IllegalArgumentException

        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun `undo and redo around adding photos to empty frame do not throw`() {
        val vm = builder(photos = listOf(photoEntity("p1"), photoEntity("p2")))
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.AddPhotos(itemId, listOf("p1", "p2")))
        assertEquals(2, vm.state.value.items.single().contents.size)

        vm.onEvent(BuilderEvent.Undo) // содержимое рамки стало пустым -> coerceIn(0, -1)
        assertTrue(vm.state.value.items.single().contents.isEmpty())

        vm.onEvent(BuilderEvent.Redo)
        assertEquals(2, vm.state.value.items.single().contents.size)
    }

    // ---------- свайп по пустой рамке ----------

    @Test
    fun `fling on empty frame does not throw and does not switch`() {
        val vm = builder()
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.ItemTapped(itemId))
        vm.onEvent(BuilderEvent.ContentFling(itemId, Direction.UP)) // было: coerceIn/mod(0)

        assertEquals(0, vm.state.value.activeIndex[itemId] ?: 0)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `set active on empty frame does not throw`() {
        val vm = builder()
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.SetActive(itemId, 0)) // было: coerceIn(0, -1)

        assertEquals(0, vm.state.value.activeIndex[itemId] ?: 0)
    }

    // ---------- выделение после redo ----------

    @Test
    fun `redo does not leave selection pointing to removed frame`() {
        val photos = listOf(photoEntity("p1"), photoEntity("p2"))
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Two"),
                    items = listOf(
                        ItemWithContents(
                            comboItemEntity("i1", "c1", 0),
                            listOf(comboContentEntity("ct1", "i1", "p1")),
                        ),
                        ItemWithContents(
                            comboItemEntity("i2", "c1", 1),
                            listOf(comboContentEntity("ct2", "i2", "p2")),
                        ),
                    ),
                ),
            ),
        )
        val vm = builder(photos = photos, comboRepo = comboRepo, comboId = "c1")
        assertEquals(2, vm.state.value.items.size)

        vm.onEvent(BuilderEvent.DeleteItem("i2"))
        vm.onEvent(BuilderEvent.Undo)
        assertEquals(2, vm.state.value.items.size)

        // Выделяем i2 (ту, которую удаляли) и делаем Redo: i2 исчезнет, а выделение
        // останется на ней — если restore() его не почистит.
        vm.onEvent(BuilderEvent.ItemTapped("i2"))
        vm.onEvent(BuilderEvent.Redo)

        val after = vm.state.value
        assertEquals(1, after.items.size)
        assertEquals("i1", after.items.single().id)
        // Выделение должно сброситься: иначе оно указывало бы на удалённую рамку i2, и
        // тулбар упал бы с NPE.
        assertNull(after.selectedItemId)
        assertTrue(after.items.none { it.id == after.selectedItemId })
    }

    // ---------- сохранение без фото ----------

    @Test
    fun `save is ignored while no frame has a photo`() {
        val comboRepo = FakeComboRepository()
        val vm = builder(comboRepo = comboRepo)

        vm.onEvent(BuilderEvent.Save)
        settle()

        assertTrue(comboRepo.savedCombos.isEmpty())
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `save failure reports message and resets saving flag`() {
        val comboRepo = FakeComboRepository().apply { failWith = IllegalStateException("db is locked") }
        val vm = builder(photos = listOf(photoEntity("p1")), comboRepo = comboRepo, photosArg = "p1")

        val messages = mutableListOf<String>()
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages += it } }
        settle()

        vm.onEvent(BuilderEvent.Save)
        // save() уходит в реальный Dispatchers.IO — ждём реального потока.
        awaitUntil { messages.isNotEmpty() }

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("db is locked"))
        assertFalse("saving должен сброситься после ошибки", vm.state.value.saving)
        assertTrue("ошибка не должна была сохранить комбо", comboRepo.savedCombos.isEmpty())
    }

    // ---------- «+Photo» в пустую рамку ----------

    @Test
    fun `photos added to empty frame become renderable`() {
        val photos = listOf(photoEntity("p1", "one.jpg"), photoEntity("p2", "two.jpg"))
        val vm = builder(photos = photos)
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.AddPhotos(itemId, listOf("p1")))
        settle()

        assertNotNull(
            "метаданные добавленного фото должны попасть в state.photos",
            vm.state.value.photos["p1"],
        )
        val item = vm.state.value.items.single { it.id == itemId }
        assertEquals(1, item.contents.size)
        assertEquals("p1", item.contents[0].photoId)
        assertNotNull("frameFor() должен вернуть кадр после загрузки битмапа", vm.frameFor(item))

        // Fit/Fill реально пересчитывают contentScale (после AddPhotos уже стоит fill).
        val border = item.border()
        val src = Size(1200f, 800f)
        vm.onEvent(BuilderEvent.ApplyPreset(itemId, FitPreset.FIT))
        val afterFit = vm.state.value.items.single { it.id == itemId }.contents[0].contentScale
        assertEquals(TransformMath.fitScale(border, src), afterFit, 0.0001f)

        vm.onEvent(BuilderEvent.ApplyPreset(itemId, FitPreset.FILL))
        val afterFill = vm.state.value.items.single { it.id == itemId }.contents[0].contentScale
        assertEquals(TransformMath.fillScale(border, src), afterFill, 0.0001f)
        assertTrue("fit и fill должны различаться", afterFill != afterFit)
    }

    @Test
    fun `adding nothing does not create an undo step`() {
        val vm = builder(photos = listOf(photoEntity("p1")))
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.AddPhotos(itemId, emptyList()))

        assertFalse("пустой AddPhotos не должен делать checkpoint", vm.state.value.canUndo)
    }

    // ---------- B3, защита №2: endGesture без изменений ----------

    @Test
    fun `gesture without changes leaves no undo step and clean dirty flag`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        val dirtyBefore = vm.state.value.isDirty
        assertFalse("предусловие: undo-пусто", vm.state.value.canUndo)

        // START + END без единой CHANGE-мутации: снапшот не изменился.
        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset.Zero, GesturePhase.START))
        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset.Zero, GesturePhase.END))

        assertFalse("жест без изменений не должен оставлять undo-шаг", vm.state.value.canUndo)
        assertEquals("isDirty не должен меняться", dirtyBefore, vm.state.value.isDirty)
    }

    @Test
    fun `gesture with changes still creates undo step`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset.Zero, GesturePhase.START))
        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset(30f, 0f), GesturePhase.CHANGE))
        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset.Zero, GesturePhase.END))

        assertTrue("реальный жест обязан дать undo-шаг", vm.state.value.canUndo)
    }

    // ---------- откат сдвига при свайпе смены фото ----------

    @Test
    fun `cancelled pan rolls content back before fling switches photo`() {
        val vm = builder(photos = listOf(photoEntity("p1"), photoEntity("p2")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.ItemTapped(itemId))
        // Второе фото в ту же рамку — так появляется item с несколькими контентами.
        vm.onEvent(BuilderEvent.AddPhotos(itemId, listOf("p2")))
        settle()
        assertEquals(2, vm.state.value.items.single().contents.size)

        // Жест свайпа: START -> CHANGE(сдвиг) -> CANCEL (так делает fling-ветка) -> Fling.
        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset.Zero, GesturePhase.START))
        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset(120f, 40f), GesturePhase.CHANGE))
        val panned = vm.state.value.items.single().contents[0]
        assertTrue("сдвиг должен был примениться", panned.panX != 0f || panned.panY != 0f)

        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset.Zero, GesturePhase.CANCEL))
        val rolledBack = vm.state.value.items.single().contents[0]
        assertEquals(0f, rolledBack.panX, 1e-3f)
        assertEquals(0f, rolledBack.panY, 1e-3f)

        vm.onEvent(BuilderEvent.ContentFling(itemId, Direction.UP))
        assertEquals("активный контент должен смениться", 1, vm.state.value.activeIndex[itemId])
    }
}
