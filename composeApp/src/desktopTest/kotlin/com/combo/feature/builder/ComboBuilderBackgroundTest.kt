package com.combo.feature.builder

import androidx.lifecycle.SavedStateHandle
import com.combo.core.background.BackgroundPresets
import com.combo.core.background.BackgroundSpec
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Фон как BackgroundSpec: состояние, undo, сохранение + фильтрация пустых рамок при save. */
@OptIn(ExperimentalCoroutinesApi::class)
class ComboBuilderBackgroundTest {

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
     * save() уходит в реальный Dispatchers.IO — тестовый диспетчер его не ждёт,
     * поэтому крутимся: прокручиваем очередь тестового диспетчера и даём реальному
     * потоку времени.
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
        photosArg: String? = null,
    ): ComboBuilderViewModel {
        val vm = ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(photosArg?.let { mapOf("photos" to it) } ?: emptyMap()),
            comboRepo = comboRepo,
            photoRepo = FakePhotoRepository(photos),
            bitmapSource = StubBitmapSource(),
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = Dispatchers.IO,
        )
        settle()
        assertTrue(vm.state.value.loaded)
        return vm
    }

    @Test
    fun `new combo starts with auto background`() {
        val vm = builder()
        assertEquals(BackgroundSpec.Auto(), vm.state.value.background)
    }

    @Test
    fun `background cycles around and undo restores previous spec`() {
        val vm = builder()
        val start = vm.state.value.background
        assertEquals(BackgroundSpec.Auto(), start)

        vm.onEvent(BuilderEvent.SetBackground(BackgroundPresets.next(start)))
        assertEquals(BackgroundSpec.Solid(0xFFFFFFFF.toInt()), vm.state.value.background)

        vm.onEvent(BuilderEvent.Undo)
        assertEquals("Undo должен вернуть прошлый фон", start, vm.state.value.background)

        // И в обратную сторону: цикл всегда возвращает в Auto после серого.
        vm.onEvent(BuilderEvent.SetBackground(BackgroundPresets.next(start)))
        vm.onEvent(BuilderEvent.SetBackground(BackgroundPresets.next(BackgroundSpec.Solid(0xFFFFFFFF.toInt()))))
        vm.onEvent(BuilderEvent.SetBackground(BackgroundPresets.next(BackgroundSpec.Solid(0xFF000000.toInt()))))
        assertEquals(BackgroundSpec.Solid(0xFF808080.toInt()), vm.state.value.background)
        vm.onEvent(BuilderEvent.SetBackground(BackgroundPresets.next(BackgroundSpec.Solid(0xFF808080.toInt()))))
        assertEquals(BackgroundSpec.Auto(), vm.state.value.background)
    }

    @Test
    fun `save writes background spec into combo entity`() {
        val spec = BackgroundSpec.Solid(0xFF00FF00.toInt())
        val comboRepo = FakeComboRepository()
        val vm = builder(photos = listOf(photoEntity("p1")), comboRepo = comboRepo, photosArg = "p1")

        vm.onEvent(BuilderEvent.SetBackground(spec))
        vm.onEvent(BuilderEvent.Save)
        awaitUntil { comboRepo.savedCombos.isNotEmpty() }

        assertEquals(1, comboRepo.savedCombos.size)
        assertEquals(spec, comboRepo.savedCombos[0].first.background)
    }

    @Test
    fun `loading existing combo reads its background spec`() {
        val spec = BackgroundSpec.Solid(0xFF123456.toInt())
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "C", background = spec),
                    items = listOf(comboItemEntity("i1", "c1", 0, photoId = "p1")),
                ),
            ),
        )
        val vm = ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo = comboRepo,
            photoRepo = FakePhotoRepository(listOf(photoEntity("p1"))),
            bitmapSource = StubBitmapSource(),
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = Dispatchers.IO,
        )
        settle()
        assertEquals(spec, vm.state.value.background)
    }

    /**
     * zIndex при сохранении перенумеровывается подряд. Слои в базе идут с «дырой» (0, 5, 9):
     * без перенумерации тест не отличил бы её от простого копирования zIndex.
     */
    @Test
    fun `save renumbers z index and keeps relative order`() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Mixed"),
                    items = listOf(
                        comboItemEntity("low", "c1", 5, photoId = "p1"),
                        comboItemEntity("high", "c1", 9, photoId = "p2"),
                        comboItemEntity("bottom", "c1", 0, photoId = "p1"),
                    ),
                ),
            ),
        )
        val vm = ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo = comboRepo,
            photoRepo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2"))),
            bitmapSource = StubBitmapSource(),
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = Dispatchers.IO,
        )
        settle()
        assertEquals(3, vm.state.value.items.size)

        vm.onEvent(BuilderEvent.Save)
        awaitUntil { comboRepo.savedCombos.isNotEmpty() }

        val saved = comboRepo.savedCombos.single()
        assertEquals("относительный порядок слоёв сохранён", listOf("bottom", "low", "high"), saved.second.map { it.id })
        assertEquals("zIndex перенумерован подряд", listOf(0, 1, 2), saved.second.map { it.zIndex })
        assertEquals("фото рамок сохранены", listOf("p1", "p1", "p2"), saved.second.map { it.photoId })
    }

    /**
     * Защита срабатывает ДО смены состояния: `saving` остаётся false, значит отправки
     * в БД не было. Проверять только список сохранённых через awaitUntil нельзя —
     * тест проходил бы и при сломанной защите (список пуст просто потому, что ещё не доехало).
     */
    @Test
    fun `save does nothing when there are no frames`() {
        val comboRepo = FakeComboRepository()
        val vm = builder(comboRepo = comboRepo)

        vm.onEvent(BuilderEvent.Save)
        assertFalse("saving не должен выставляться, если сохранять нечего", vm.state.value.saving)

        settle()
        assertTrue(comboRepo.savedCombos.isEmpty())
    }
}
