package com.combo.feature.builder

import androidx.lifecycle.SavedStateHandle
import com.combo.core.background.BackgroundSpec
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.comboContentEntity
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

/** Фон как BackgroundSpec: состояние, undo, сохранение (A6) + фильтрация пустых рамок (B2). */
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

    private fun builder(
        photos: List<com.combo.data.PhotoEntity> = emptyList(),
        comboRepo: FakeComboRepository = FakeComboRepository(),
        photosArg: String? = null,
    ): ComboBuilderViewModel {
        val vm = ComboBuilderViewModel(
            SavedStateHandle(photosArg?.let { mapOf("photos" to it) } ?: emptyMap()),
            comboRepo,
            FakePhotoRepository(photos),
            StubBitmapSource(),
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

        vm.onEvent(BuilderEvent.SetBackground(com.combo.core.background.BackgroundPresets.next(start)))
        assertEquals(BackgroundSpec.Solid(0xFFFFFFFF.toInt()), vm.state.value.background)

        vm.onEvent(BuilderEvent.Undo)
        assertEquals("Undo должен вернуть прошлый фон", start, vm.state.value.background)

        // И в обратную сторону: цикл всегда возвращает в Auto после серого.
        vm.onEvent(BuilderEvent.SetBackground(com.combo.core.background.BackgroundPresets.next(start)))
        vm.onEvent(BuilderEvent.SetBackground(com.combo.core.background.BackgroundPresets.next(BackgroundSpec.Solid(0xFFFFFFFF.toInt()))))
        vm.onEvent(BuilderEvent.SetBackground(com.combo.core.background.BackgroundPresets.next(BackgroundSpec.Solid(0xFF000000.toInt()))))
        assertEquals(BackgroundSpec.Solid(0xFF808080.toInt()), vm.state.value.background)
        vm.onEvent(BuilderEvent.SetBackground(com.combo.core.background.BackgroundPresets.next(BackgroundSpec.Solid(0xFF808080.toInt()))))
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
                    items = listOf(
                        ItemWithContents(
                            comboItemEntity("i1", "c1", 0),
                            listOf(comboContentEntity("ct1", "i1", "p1")),
                        ),
                    ),
                ),
            ),
        )
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo,
            FakePhotoRepository(listOf(photoEntity("p1"))),
            StubBitmapSource(),
        )
        settle()
        assertEquals(spec, vm.state.value.background)
    }

    /**
     * B2: пустые рамки в базу не пишем, zIndex перенумеровывается подряд.
     * Пустая рамка стоит на слое 0, заполненные — на 1 и 2, иначе перенумерацию
     * нечем проверить: у единственной оставшейся рамки zIndex и так был бы 0.
     */
    @Test
    fun `save drops empty frames and renumbers z index`() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Mixed"),
                    items = listOf(
                        ItemWithContents(comboItemEntity("empty", "c1", 0), emptyList()),
                        ItemWithContents(
                            comboItemEntity("low", "c1", 1),
                            listOf(comboContentEntity("ct1", "low", "p1")),
                        ),
                        ItemWithContents(
                            comboItemEntity("high", "c1", 2),
                            listOf(comboContentEntity("ct2", "high", "p2")),
                        ),
                    ),
                ),
            ),
        )
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo,
            FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2"))),
            StubBitmapSource(),
        )
        settle()
        assertEquals(3, vm.state.value.items.size)

        vm.onEvent(BuilderEvent.Save)
        awaitUntil { comboRepo.savedCombos.isNotEmpty() }

        val saved = comboRepo.savedCombos.single()
        assertEquals("пустая рамка не сохраняется", 2, saved.second.size)
        assertEquals("относительный порядок слоёв сохранён", listOf("low", "high"), saved.second.map { it.id })
        assertEquals("zIndex перенумерован подряд", listOf(0, 1), saved.second.map { it.zIndex })
        assertEquals(listOf("ct1", "ct2"), saved.third.map { it.id })
    }

    /**
     * Защита срабатывает ДО смены состояния: `saving` остаётся false, значит отправки
     * в БД не было. Проверять только список сохранённых через awaitUntil нельзя —
     * тест проходил бы и при сломанной защите (список пуст просто потому, что ещё не доехало).
     */
    @Test
    fun `save does nothing when every frame is empty`() {
        val comboRepo = FakeComboRepository()
        val vm = builder(comboRepo = comboRepo)

        vm.onEvent(BuilderEvent.Save)
        assertFalse("saving не должен выставляться, если сохранять нечего", vm.state.value.saving)

        settle()
        assertTrue(comboRepo.savedCombos.isEmpty())
    }
}