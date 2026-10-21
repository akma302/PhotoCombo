package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.SavedStateHandle
import com.combo.core.model.FramingMode
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.core.render.Border
import com.combo.core.render.ItemDraft
import com.combo.core.render.Edge
import com.combo.core.render.LayoutPresets
import com.combo.core.render.TransformMath
import com.combo.core.render.effectiveFraming
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.feature.builder.BuilderEvent
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.builder.GesturePhase
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Режим рамки AutoFit (`FramingMode.FIT`, «persistent fit»).
 *
 * Инварианты, которые здесь защищаются:
 *  - FIT — это вписанный масштаб и нулевой pan: сохранённые значения в FIT не показываются,
 *    но записываются как есть при выходе из режима («запекаются»);
 *  - картинка не «скачет» ни при переводе формата холста, ни при ресайзе/щипке рамки;
 *  - сохранение/чтение в БД и undo/redo не теряют режим.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoFitTest {

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

    // ---------- чистая функция ----------

    private fun frame(w: Float, h: Float) = Border(0f, 0f, w, h)

    @Test
    fun `FREE returns stored values untouched`() {
        val f = effectiveFraming(FramingMode.FREE, frame(200f, 100f), 100, 200, 3.5f, 7f, -9f)
        assertEquals(3.5f, f.scale, 0f)
        assertEquals(7f, f.panX, 0f)
        assertEquals(-9f, f.panY, 0f)
    }

    /** Широкая рамка 200x100 и высокое фото 100x200: ограничивает высота → scale = 0.5. */
    @Test
    fun `FIT fills the constraining side of a wide frame`() {
        val f = effectiveFraming(FramingMode.FIT, frame(200f, 100f), 100, 200, 1f, 5f, 5f)
        assertEquals("фото должно занять всю высоту рамки", 0.5f, f.scale, 1e-6f)
        assertEquals("pan в FIT всегда нулевой (центр)", 0f, f.panX, 0f)
        assertEquals(0f, f.panY, 0f)
    }

    @Test
    fun `FIT fills the constraining side of a tall frame`() {
        val f = effectiveFraming(FramingMode.FIT, frame(100f, 200f), 200, 100, 1f, 5f, 5f)
        assertEquals("фото должно занять всю ширину рамки", 0.5f, f.scale, 1e-6f)
        assertEquals(0f, f.panX, 0f)
        assertEquals(0f, f.panY, 0f)
    }

    @Test
    fun `FIT of square in square is exact`() {
        assertEquals(1f, effectiveFraming(FramingMode.FIT, frame(100f, 100f), 100, 100, 1f, 0f, 0f).scale, 1e-6f)
        assertEquals(2f, effectiveFraming(FramingMode.FIT, frame(200f, 200f), 100, 100, 1f, 0f, 0f).scale, 1e-6f)
    }

    /** Фото крупнее рамки вписывается целиком — то есть УМЕНЬШАЕТСЯ, а не заполняет. */
    @Test
    fun `FIT shrinks a photo larger than the frame`() {
        val f = effectiveFraming(FramingMode.FIT, frame(100f, 100f), 800, 800, 9f, 0f, 0f)
        assertEquals(0.125f, f.scale, 1e-6f)
    }

    /** Нулевые размеры фото → возвращаем сохранённые, иначе scale ушёл бы в NaN/Inf. */
    @Test
    fun `FIT falls back to stored values for unknown photo size`() {
        for (dims in listOf(0 to 0, 100 to 0, 0 to 100)) {
            val f = effectiveFraming(FramingMode.FIT, frame(200f, 100f), dims.first, dims.second, 2f, 3f, 4f)
            assertEquals("размеры $dims", 2f, f.scale, 0f)
            assertEquals(3f, f.panX, 0f)
            assertEquals(4f, f.panY, 0f)
        }
    }

    /**
     * Одна общая шкала по обеим осям: иначе фото растянулось бы (а WYSIWYG-инвариант
     * «контент всегда с сохранением аспекта» перестал бы выполняться).
     */
    @Test
    fun `FIT uses one scale for both axes so proportions are kept`() {
        val f = effectiveFraming(FramingMode.FIT, frame(200f, 100f), 300, 200, 1f, 0f, 0f)
        val drawn = TransformMath.contentRect(frame(200f, 100f), 300, 200, f.scale, Offset.Zero)
        val expected = 300f / 200f
        assertEquals(expected, drawn.width / drawn.height, 1e-4f)
    }

    /** FIT обязан совпадать с кнопкой «Fit»: одна формула, а не две реализации. */
    @Test
    fun `FIT scale equals LayoutPresets fit`() {
        val b = frame(180f, 220f)
        val draft = ItemDraft("i", 0, 0f, 0f, 180f, 220f, photoId = "p", contentScale = 9f, panX = 1f, panY = 2f)
        LayoutPresets.fit(draft, b, Size(320f, 240f))
        val f = effectiveFraming(FramingMode.FIT, b, 320, 240, 9f, 1f, 2f)
        assertEquals(draft.contentScale, f.scale, 1e-6f)
        assertEquals(0f, draft.panX, 0f)
        assertEquals(0f, draft.panY, 0f)
    }

    // ---------- ViewModel ----------

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

    /** Фото 1200x800 (см. [photoEntity]) в рамке 1600x900 → вписанный масштаб. */
    private fun fitScaleOf(vm: ComboBuilderViewModel, index: Int = 0): Float {
        val item = vm.state.value.items[index]
        val photo = vm.state.value.photos[item.photoId] ?: error("нет фото")
        val f = effectiveFraming(
            item.framingMode, item.border(), photo.width, photo.height,
            item.contentScale, item.panX, item.panY,
        )
        return f.scale
    }

    private fun drawnScale(vm: ComboBuilderViewModel, itemId: String): Float? =
        vm.state.value.items.firstOrNull { it.id == itemId }
            ?.let { vm.frameFor(it) }?.content?.scale

    @Test
    fun `new frames default to FREE`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        assertEquals(FramingMode.FREE, vm.state.value.items.single().framingMode)
    }

    @Test
    fun `SetFramingMode FIT makes the drawn scale the fitted one`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        settle()

        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))

        assertEquals(FramingMode.FIT, vm.state.value.items.single().framingMode)
        val item = vm.state.value.items.single()
        val expected = TransformMath.fitScale(item.border(), Size(1200f, 800f))
        assertEquals(expected, fitScaleOf(vm), 1e-6f)
        assertEquals(expected, drawnScale(vm, itemId)!!, 1e-6f)
        assertEquals(0f, vm.frameFor(item)!!.content.pan.x, 0f)
    }

    /**
     * Ресайз рамки в FIT пересчитывает картинку сам — событий для контента не нужно.
     * Ресайзим нижнюю грань: фото 1200x800 вписывается по высоте 900, и пока высота
     * ограничивает, изменение ШИРИНЫ не меняет вписанный масштаб вовсе.
     */
    @Test
    fun `BorderResize in FIT changes the fitted scale without touching content`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))
        val before = drawnScale(vm, itemId)!!
        val storedBefore = vm.state.value.items.single().contentScale

        vm.onEvent(BuilderEvent.BorderResize(itemId, Edge.BOTTOM, Offset(0f, 200f), GesturePhase.CHANGE))

        val after = drawnScale(vm, itemId)!!
        assertNotEquals("вписанный масштаб обязан измениться вместе с рамкой", before, after)
        assertEquals(
            "хранимые значения в FIT не участвуют в отрисовке и не меняются",
            storedBefore,
            vm.state.value.items.single().contentScale,
        )
        assertEquals(after, fitScaleOf(vm), 1e-6f)
    }

    /**
     * `ItemPinch` в FIT масштабирует хранимые значения вместе с рамкой, но на картинку они
     * не влияют: её по-прежнему задаёт вписывание.
     */
    @Test
    fun `ItemPinch in FIT rescales frame and content but picture stays fitted`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))
        val before = drawnScale(vm, itemId)!!
        val storedBefore = vm.state.value.items.single().contentScale

        vm.onEvent(BuilderEvent.ItemPinch(itemId, 1.5f, GesturePhase.CHANGE))

        val item = vm.state.value.items.single()
        assertEquals("рамка выросла в 1.5 раза", 1.5f, item.width / 1600f, 1e-4f)
        assertEquals(
            "вписанный масштаб растёт вместе с рамкой (щипок тянет обе оси)",
            before * 1.5f,
            drawnScale(vm, itemId)!!,
            1e-4f,
        )
        assertEquals(
            "хранимые значения меняются вместе с рамкой, но в FIT не показываются",
            storedBefore * 1.5f,
            item.contentScale,
            1e-4f,
        )
        assertEquals(drawnScale(vm, itemId)!!, fitScaleOf(vm), 1e-6f)
    }

    /** Pan/zoom контента в FIT игнорируются и не оставляют undo-шага. */
    @Test
    fun `ContentPan and ContentZoom in FIT change nothing and leave no undo step`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))
        val canUndoBefore = vm.state.value.canUndo
        val contentBefore = vm.state.value.items.single()
        val (scaleBefore, panXBefore, panYBefore) =
            Triple(contentBefore.contentScale, contentBefore.panX, contentBefore.panY)
        val drawnBefore = drawnScale(vm, itemId)

        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset.Zero, GesturePhase.START))
        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset(200f, 90f), GesturePhase.CHANGE))
        vm.onEvent(BuilderEvent.ContentZoom(itemId, 2f, GesturePhase.CHANGE))
        vm.onEvent(BuilderEvent.ContentPan(itemId, Offset.Zero, GesturePhase.END))

        val c = vm.state.value.items.single()
        assertEquals(scaleBefore, c.contentScale, 0f)
        assertEquals(panXBefore, c.panX, 0f)
        assertEquals(panYBefore, c.panY, 0f)
        assertEquals(drawnBefore, drawnScale(vm, itemId))
        assertEquals(
            "жест, ничего не изменивший, не должен добавлять undo-шаг",
            canUndoBefore,
            vm.state.value.canUndo,
        )
    }

    /**
     * Главный UX-инвариант AutoFit: выход в FREE через кнопку НЕ меняет картинку —
     * вычисленные значения «запекаются» в draft.
     */
    @Test
    fun `switching back to FREE does not change the picture`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))
        // Рамка меняется, пока фото в FIT — «запекать» надо то, что видно СЕЙЧАС.
        vm.onEvent(BuilderEvent.BorderResize(itemId, Edge.BOTTOM, Offset(0f, -200f), GesturePhase.CHANGE))
        val before = drawnScale(vm, itemId)!!

        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FREE))

        assertEquals(FramingMode.FREE, vm.state.value.items.single().framingMode)
        assertEquals("картинка обязана остаться на месте", before, drawnScale(vm, itemId)!!, 1e-6f)
        assertEquals(0f, vm.frameFor(vm.state.value.items.single())!!.content.pan.x, 0f)
    }

    /**
     * Пресет в FIT — явный выбор пользователя, он важнее блокировки: режим становится
     * FREE, а фото получает значения пресета.
     */
    @Test
    fun `ApplyPreset in FIT leaves FREE and applies the preset`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))

        vm.onEvent(BuilderEvent.ApplyPreset(itemId, FitPreset.FILL))

        val after = vm.state.value.items.single()
        assertEquals("пресет обязан вывести рамку из FIT", FramingMode.FREE, after.framingMode)
        assertEquals(
            "фото получило fill",
            TransformMath.fillScale(after.border(), Size(1200f, 800f)),
            after.contentScale,
            1e-4f,
        )
        assertEquals(0f, after.panX, 0f)
        assertEquals(0f, after.panY, 0f)
    }

    /** Запрос на уже стоящий режим — не правка: ни undo-шага, ни пометки «изменено». */
    @Test
    fun `SetFramingMode to the current mode leaves no undo step and no dirty flag`() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("combo1"),
                    items = listOf(comboItemEntity("i1", "combo1", 0, photoId = "p1")),
                ),
            ),
        )
        val vm = builder(photos = listOf(photoEntity("p1")), comboRepo = comboRepo, comboId = "combo1")
        assertTrue("открытое комбо не изменено", !vm.state.value.isDirty)
        assertTrue(!vm.state.value.canUndo)

        vm.onEvent(BuilderEvent.SetFramingMode("i1", FramingMode.FREE))

        assertEquals(FramingMode.FREE, vm.state.value.items.single().framingMode)
        assertTrue("режим не менялся — комбо не должно стать изменённым", !vm.state.value.isDirty)
        assertTrue("режим не менялся — undo-шага быть не должно", !vm.state.value.canUndo)

        vm.onEvent(BuilderEvent.SetFramingMode("i1", FramingMode.FIT))

        assertTrue("смена режима — обычная правка", vm.state.value.isDirty)
        assertTrue(vm.state.value.canUndo)
        vm.onEvent(BuilderEvent.Undo)
        assertEquals(FramingMode.FREE, vm.state.value.items.single().framingMode)
    }

    @Test
    fun `undo and redo restore the framing mode`() {
        val vm = builder(photos = listOf(photoEntity("p1")), photosArg = "p1")
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))
        assertEquals(FramingMode.FIT, vm.state.value.items.single().framingMode)

        vm.onEvent(BuilderEvent.Undo)
        assertEquals(FramingMode.FREE, vm.state.value.items.single().framingMode)

        vm.onEvent(BuilderEvent.Redo)
        assertEquals(FramingMode.FIT, vm.state.value.items.single().framingMode)
    }

    // ---------- сохранение в БД ----------

    @Test
    fun `save writes framing mode and loading reads it back`() {
        val comboRepo = FakeComboRepository()
        val vm = builder(
            photos = listOf(photoEntity("p1")), comboRepo = comboRepo, photosArg = "p1",
        )
        val itemId = vm.state.value.items.single().id
        vm.onEvent(BuilderEvent.SetFramingMode(itemId, FramingMode.FIT))

        vm.onEvent(BuilderEvent.Save)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && comboRepo.savedCombos.isEmpty()) {
            settle()
            Thread.sleep(5)
        }
        assertTrue(
            "комбо не сохранилось (loaded=${vm.state.value.loaded}, saving=${vm.state.value.saving}, " +
                "items=${vm.state.value.items.size})",
            comboRepo.savedCombos.isNotEmpty(),
        )
        assertEquals("FIT", comboRepo.savedCombos.single().second.single().framingMode)

        // Round-trip: читаем сохранённое обратно в новый билдер — по НАСТОЯЩЕМУ id,
        // иначе loadExisting("c1") не нашёл бы ничего и «успех» был бы пустым.
        val savedCombo = comboRepo.savedCombos.single()
        val savedItem = savedCombo.second.single()
        val readBack = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = savedCombo.first,
                    items = listOf(savedItem),
                ),
            ),
        )
        val vm2 = builder(comboRepo = readBack, comboId = savedCombo.first.id)
        assertEquals(
            "режим рамки обязан пережить сохранение и загрузку",
            FramingMode.FIT,
            vm2.state.value.items.single().framingMode,
        )
    }

    @Test
    fun `loaded combo keeps FREE by default`() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "C"),
                    items = listOf(comboItemEntity("i1", "c1", 0, photoId = "p1")),
                ),
            ),
        )
        val vm = builder(comboRepo = comboRepo, comboId = "c1")
        assertEquals(FramingMode.FREE, vm.state.value.items.single().framingMode)
    }
}
