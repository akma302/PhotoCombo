package com.combo

import androidx.lifecycle.SavedStateHandle
import com.combo.core.model.Direction
import com.combo.data.ComboEntity
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.data.PhotoEntity
import com.combo.feature.builder.BuilderEvent
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Регрессионный тест: «текущий» индекс фото в редакторе не должен расходиться с
 * анимацией переключения.
 *
 * Индекс текущего фото хранится в двух местах: `BuilderUiState.activeIndex` (что рисует
 * `frameFor`) и внутри `SwitchAnimator` (оттуда берётся `from` для перехода). Когда индекс
 * меняется не свайпом, а удалением фото или undo/redo, редактор обязан обновить оба,
 * иначе следующий свайп стартует из устаревшего `from`: либо переход не проигрывается,
 * либо свайп не делает ничего.
 *
 * Фото в тестах разного размера, чтобы по `srcW` кадра опознавать, какое фото исходящее,
 * а какое входящее.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackIndexSyncTest {

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

    /** Фото разного размера: по `srcW` кадра можно опознать, ЧЬЁ это фото. */
    private fun photo(id: String, size: Int) = PhotoEntity(
        id = id,
        fileName = "$id.jpg",
        originalName = "$id.jpg",
        mimeType = "image/jpeg",
        width = size,
        height = size,
        sizeBytes = 1,
        sha256 = "sha-$id",
        avgColor = 0xFF808080.toInt(),
        importedAt = 0L,
    )

    private val photos = listOf(photo("p1", 100), photo("p2", 200), photo("p3", 300))

    private fun combo(): ComboWithItems = ComboWithItems(
        combo = ComboEntity(
            id = "c1",
            name = "Sync",
            canvasWidth = 1600,
            canvasHeight = 900,
            background = com.combo.core.background.BackgroundSpec.Auto(),
            createdAt = 0L,
            updatedAt = 0L,
        ),
        items = listOf(
            ItemWithContents(
                item = ComboItemEntity(
                    id = "i1",
                    comboId = "c1",
                    zIndex = 0,
                    centerX = 800f,
                    centerY = 450f,
                    width = 1600f,
                    height = 900f,
                    // SLIDE — ненулевая длительность: переход реально попадает в animator.
                    switchMode = "MANUAL",
                    manualTransition = "SLIDE",
                    autoTransition = "FADE",
                    framingMode = "FREE",
                ),
                contents = listOf(
                    content("ct1", "p1", 0),
                    content("ct2", "p2", 1),
                    content("ct3", "p3", 2),
                ),
            ),
        ),
    )

    private fun content(id: String, photoId: String, order: Int) = ComboItemContentEntity(
        id = id,
        itemId = "i1",
        photoId = photoId,
        orderIndex = order,
        contentScale = 1f,
        panX = 0f,
        panY = 0f,
    )

    private fun builder(): ComboBuilderViewModel {
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            FakeComboRepository(listOf(combo())),
            FakePhotoRepository(photos),
            StubBitmapSource(),
        )
        settle()
        assertEquals(true, vm.state.value.loaded)
        // Битмапы нужны для frameFor(); immediate = true — чтобы не ждать debounce.
        vm.refreshBitmaps(immediate = true)
        settle()
        assertEquals("все три битмапа должны быть загружены", 3, vm.bitmaps.value.size)
        return vm
    }

    @Test
    fun `after removing the active photo the next fling still plays a transition`() {
        val vm = builder()
        val itemId = vm.state.value.items.single().id

        // Видим третье фото.
        vm.onEvent(BuilderEvent.SetActive(itemId, 2))
        assertEquals(2, vm.activeIndexOf(vm.state.value.items.single()))

        // Удаляем его: остаётся два фото, активным становится p2.
        vm.onEvent(BuilderEvent.RemoveActiveContent(itemId))
        val afterRemove = vm.state.value.items.single()
        assertEquals(2, afterRemove.contents.size)
        assertEquals("p2", afterRemove.contents[1].photoId)
        assertEquals(1, vm.activeIndexOf(afterRemove))

        // Свайп вверх: p2 -> p1, переход ОБЯЗАН проиграться.
        vm.onEvent(BuilderEvent.ContentFling(itemId, Direction.UP))

        val frame = vm.frameFor(vm.state.value.items.single())
        assertNotNull("кадр должен собираться", frame)
        assertNotNull(
            "после удаления активного фото переход не проигрывается: from остался 2, " +
                "а фото с индексом 2 больше нет",
            frame!!.transition,
        )
        assertNotNull("исходящее фото должно быть тем, что пользователь видел (p2)", frame.outgoing)
        assertEquals("исходящее фото = p2", 200, frame.outgoing!!.srcW)
        assertEquals("входящее фото = p1", 100, frame.incoming.srcW)
    }

    /**
     * То же через undo: `restore()` пересобирает драфты из снимка и подгоняет
     * `activeIndex`, и индекс в `SwitchAnimator` должен быть синхронизирован с ним. Если
     * этого не сделать, симптом хуже, чем при удалении: после отката активен индекс 1, свайп
     * вверх ведёт на 2, а `animator.switch(from = 2, to = 2)` завершается ранним выходом
     * (`from == to`), и свайп не делает ничего.
     */
    @Test
    fun `after undo the next fling still switches photo and plays a transition`() {
        val vm = builder()
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.SetActive(itemId, 2))
        vm.onEvent(BuilderEvent.RemoveActiveContent(itemId))
        assertEquals(2, vm.state.value.items.single().contents.size)

        vm.onEvent(BuilderEvent.Undo)

        val restored = vm.state.value.items.single()
        assertEquals("undo обязан вернуть три фото", 3, restored.contents.size)

        vm.onEvent(BuilderEvent.ContentFling(itemId, Direction.UP))
        val frame = vm.frameFor(vm.state.value.items.single())
        assertNotNull("кадр должен собираться", frame)
        assertNotNull(
            "после undo свайп обязан проигрывать переход (from == to уходит ранним выходом)",
            frame!!.transition,
        )
        assertEquals("свайп вверх обязан переключить фото", 2, vm.activeIndexOf(restored))
        // После undo видно p2 (индекс 1), свайп вверх ведёт к p3.
        assertEquals("исходящее фото = p2", 200, frame.outgoing!!.srcW)
        assertEquals("входящее фото = p3", 300, frame.incoming.srcW)
    }
}
