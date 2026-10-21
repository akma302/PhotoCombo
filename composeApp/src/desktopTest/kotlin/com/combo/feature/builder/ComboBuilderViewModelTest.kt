package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.SavedStateHandle
import com.combo.Const
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.core.render.TransformMath
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
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
 * Поведение `ComboBuilderViewModel` на крайних случаях: комбо без рамок, выделение после
 * redo, сохранение, «+Frame» (N фото → N рамок), замена фото, откат отменённого жеста.
 * Чистый JVM: Compose здесь не запускается, UI не нужен.
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

    private var nextId = 0

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
            savedStateHandle = handle,
            comboRepo = comboRepo,
            photoRepo = FakePhotoRepository(photos),
            bitmapSource = StubBitmapSource(),
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = Dispatchers.IO,
        )
        settle()
        assertTrue("билдер не загрузился", vm.state.value.loaded)
        return vm
    }

    // ---------- комбо без рамок ----------

    @Test
    fun `new combo without photos has no frames and no selection`() {
        val vm = builder()

        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.selectedItemId)
    }

    @Test
    fun `undo after aspect change on combo without frames does not throw`() {
        val vm = builder()

        vm.onEvent(BuilderEvent.SetAspect(900, 1600))
        assertTrue(vm.state.value.canUndo)

        vm.onEvent(BuilderEvent.Undo)

        assertTrue(vm.state.value.items.isEmpty())
        assertEquals(1600f, vm.state.value.canvasW, 0f)
    }

    @Test
    fun `new combo from three photos gets three frames with one photo each`() {
        val ids = listOf("p1", "p2", "p3")
        val vm = builder(photos = ids.map { photoEntity(it) }, photosArg = ids.joinToString(","))

        assertEquals(3, vm.state.value.items.size)
        assertEquals(ids, vm.state.value.items.map { it.photoId })
        assertEquals("слои идут подряд", listOf(0, 1, 2), vm.state.value.items.map { it.zIndex })
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
                        comboItemEntity("i1", "c1", 0, photoId = "p1"),
                        comboItemEntity("i2", "c1", 1, photoId = "p2"),
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

    // ---------- сохранение ----------

    @Test
    fun `save is ignored while there are no frames`() {
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

    // ---------- «+Frame»: N фото → N рамок ----------

    @Test
    fun `adding photos to empty combo creates renderable frames`() {
        val photos = listOf(photoEntity("p1", "one.jpg"), photoEntity("p2", "two.jpg"))
        val vm = builder(photos = photos)

        vm.onEvent(BuilderEvent.AddItems(listOf("p1", "p2")))
        settle()

        assertEquals("N фото дают N рамок", 2, vm.state.value.items.size)
        assertNotNull(
            "метаданные добавленного фото должны попасть в state.photos",
            vm.state.value.photos["p1"],
        )
        val first = vm.state.value.items[0]
        assertEquals("p1", first.photoId)
        assertEquals("p2", vm.state.value.items[1].photoId)
        assertEquals("выделена последняя добавленная рамка", vm.state.value.items[1].id, vm.state.value.selectedItemId)
        assertNotNull("frameFor() должен вернуть кадр после загрузки битмапа", vm.frameFor(first))

        // Fit/Fill реально пересчитывают contentScale.
        val border = first.border()
        val src = Size(1200f, 800f)
        vm.onEvent(BuilderEvent.ApplyPreset(first.id, FitPreset.FIT))
        val afterFit = vm.state.value.items.single { it.id == first.id }.contentScale
        assertEquals(TransformMath.fitScale(border, src), afterFit, 0.0001f)

        vm.onEvent(BuilderEvent.ApplyPreset(first.id, FitPreset.FILL))
        val afterFill = vm.state.value.items.single { it.id == first.id }.contentScale
        assertEquals(TransformMath.fillScale(border, src), afterFill, 0.0001f)
        assertTrue("fit и fill должны различаться", afterFill != afterFit)
    }

    @Test
    fun `adding nothing does not create an undo step`() {
        val vm = builder(photos = listOf(photoEntity("p1")))

        vm.onEvent(BuilderEvent.AddItems(emptyList()))

        assertFalse("пустой AddItems не должен делать checkpoint", vm.state.value.canUndo)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `adding a frame selects it, marks dirty and leaves one undo step`() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "One"),
                    items = listOf(comboItemEntity("i1", "c1", 0, photoId = "p1")),
                ),
            ),
        )
        val vm = builder(photos = listOf(photoEntity("p1"), photoEntity("p2")), comboRepo = comboRepo, comboId = "c1")
        assertFalse("предусловие: комбо только что загружено", vm.state.value.isDirty)

        vm.onEvent(BuilderEvent.AddItems(listOf("p2")))

        val added = vm.state.value.items.last()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("p2", added.photoId)
        assertEquals("новая рамка сразу выделена", added.id, vm.state.value.selectedItemId)
        assertTrue("добавление рамки — правка", vm.state.value.isDirty)
        assertTrue(vm.state.value.canUndo)

        vm.onEvent(BuilderEvent.Undo)
        assertEquals(1, vm.state.value.items.size)
        // Один шаг undo убирает все рамки одного добавления; второго шага нет.
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `adding several photos is one undo step`() {
        val vm = builder(photos = listOf(photoEntity("p1"), photoEntity("p2"), photoEntity("p3")))

        vm.onEvent(BuilderEvent.AddItems(listOf("p1", "p2", "p3")))
        assertEquals(3, vm.state.value.items.size)

        vm.onEvent(BuilderEvent.Undo)

        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.canUndo)
        assertTrue(vm.state.value.canRedo)
    }

    @Test
    fun `the same photo twice in one request gives one frame`() {
        val vm = builder(photos = listOf(photoEntity("p1")))

        vm.onEvent(BuilderEvent.AddItems(listOf("p1", "p1")))

        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun `adding beyond the item limit does nothing and leaves no undo step`() {
        val ids = List(Const.MAX_ITEMS) { "p$it" }
        val vm = builder(photos = (ids + "extra").map { photoEntity(it) }, photosArg = ids.joinToString(","))
        assertEquals(Const.MAX_ITEMS, vm.state.value.items.size)

        vm.onEvent(BuilderEvent.AddItems(listOf("extra")))

        assertEquals(Const.MAX_ITEMS, vm.state.value.items.size)
        assertFalse("лимит — не правка: undo-шага быть не должно", vm.state.value.canUndo)
    }

    @Test
    fun `photos over the limit are cut off when adding`() {
        val ids = List(Const.MAX_ITEMS + 3) { "p$it" }
        val vm = builder(photos = ids.map { photoEntity(it) })

        vm.onEvent(BuilderEvent.AddItems(ids))

        assertEquals(Const.MAX_ITEMS, vm.state.value.items.size)
        assertEquals(ids.take(Const.MAX_ITEMS), vm.state.value.items.map { it.photoId })
    }

    // ---------- замена фото ----------

    @Test
    fun `replace photo keeps frame geometry and undo returns old photo`() {
        val vm = builder(photos = listOf(photoEntity("p1"), photoEntity("p2")), photosArg = "p1")
        val item = vm.state.value.items.single()
        val before = listOf(item.centerX, item.centerY, item.width, item.height, item.rotation)

        vm.onEvent(BuilderEvent.ReplacePhoto(item.id, "p2"))
        settle()

        val replaced = vm.state.value.items.single()
        assertEquals("id рамки не меняется", item.id, replaced.id)
        assertEquals("p2", replaced.photoId)
        assertEquals(
            "положение и размер рамки не меняются",
            before,
            listOf(replaced.centerX, replaced.centerY, replaced.width, replaced.height, replaced.rotation),
        )
        assertTrue(vm.state.value.isDirty)
        assertNotNull("метаданные нового фото известны", vm.state.value.photos["p2"])

        vm.onEvent(BuilderEvent.Undo)
        assertEquals("p1", vm.state.value.items.single().photoId)
        vm.onEvent(BuilderEvent.Redo)
        assertEquals("p2", vm.state.value.items.single().photoId)
    }

    @Test
    fun `replace with the same photo is not an edit`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val id = vm.state.value.items.single().id
        val dirtyBefore = vm.state.value.isDirty

        vm.onEvent(BuilderEvent.ReplacePhoto(id, "p1"))

        assertFalse("замена на то же фото не даёт undo-шага", vm.state.value.canUndo)
        assertEquals(dirtyBefore, vm.state.value.isDirty)
    }

    @Test
    fun `replace with unknown photo does nothing`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val id = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.ReplacePhoto(id, "missing"))

        assertEquals("p1", vm.state.value.items.single().photoId)
        assertFalse(vm.state.value.canUndo)
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

    // ---------- отмена жеста ----------

    @Test
    fun `cancelled pan rolls content back and leaves no undo step`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.ItemTapped(itemId))
        settle()

        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset.Zero, GesturePhase.START))
        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset(120f, 40f), GesturePhase.CHANGE))
        val panned = vm.state.value.items.single()
        assertTrue("сдвиг должен был примениться", panned.panX != 0f || panned.panY != 0f)

        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset.Zero, GesturePhase.CANCEL))
        val rolledBack = vm.state.value.items.single()
        assertEquals(0f, rolledBack.panX, 1e-3f)
        assertEquals(0f, rolledBack.panY, 1e-3f)
        assertFalse("отменённый жест не попадает в undo", vm.state.value.canUndo)
    }
}
