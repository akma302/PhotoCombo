package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.TestScreen
import com.combo.ui.UiTestBase
import com.combo.ui.photoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Колесо мыши на холсте редактора — замена щипка: цель выбирается как у касаний (чужая
 * рамка в EDIT масштабируется целиком, выбранная в BORDER — только её фото, пустое место —
 * вид), а серия тиков копится в ОДИН undo-шаг.
 *
 * Ctrl+колесо (всегда вид) UI-тестом не покрыто: инжектор мыши skiko-тестов отправляет
 * события без модификаторов клавиатуры, поэтому Ctrl-ветку проверяют руками.
 *
 * Координаты ввода — ЛОКАЛЬНЫЕ для узла холста (так их принимает `performMouseInput`) и
 * считаются по тем же формулам, что и сам холст редактора. Пауза серии колеса — это
 * задержка в pointerInput-корутине: в skiko-тестах её двигает только mainClock, поэтому
 * после серии нужен `advanceTimeBy`.
 */
@OptIn(ExperimentalTestApi::class)
class EditorWheelTest : UiTestBase() {

    private var nextId = 0

    private fun builder(photoIds: List<String>, repo: FakePhotoRepository): ComboBuilderViewModel =
        ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("photos" to photoIds.joinToString(","))),
            comboRepo = FakeComboRepository(),
            photoRepo = repo,
            bitmapSource = StubBitmapSource(),
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = ioDispatcher,
        )

    private fun ComposeUiTest.setBuilderContent(vm: ComboBuilderViewModel, repo: FakePhotoRepository) {
        val koin = trackKoin(repo)
        setContent {
            TestScreen(koin, backDispatcher) { ComboBuilderScreen(onBack = {}, vm = vm) }
        }
        waitUntil(timeoutMillis = 5_000) {
            vm.state.value.loaded && vm.state.value.screenW > 0f
        }
    }

    /** Локальная точка узла холста для координаты холста — та же формула, что в EditorViewport. */
    private fun local(st: BuilderUiState, x: Float, y: Float): Offset {
        val vs = st.viewport.scale(
            Size(st.canvasW, st.canvasH),
            Size(st.screenW, st.screenH),
        )
        val originX = (st.screenW - st.canvasW * vs) / 2 + st.viewport.pan.x
        val originY = (st.screenH - st.canvasH * vs) / 2 + st.viewport.pan.y
        return Offset(originX + x * vs, originY + y * vs)
    }

    /** Пустое место холста — середина полосы letterbox (по вертикали или по горизонтали). */
    private fun emptySpotLocal(st: BuilderUiState): Offset {
        val vs = st.viewport.scale(
            Size(st.canvasW, st.canvasH),
            Size(st.screenW, st.screenH),
        )
        val padX = (st.screenW - st.canvasW * vs) / 2
        val padY = (st.screenH - st.canvasH * vs) / 2
        return if (padY >= padX) Offset(st.screenW / 2, padY / 2) else Offset(padX / 2, st.screenH / 2)
    }

    private fun centerOf(st: BuilderUiState, index: Int): Offset {
        val b = st.items[index].border()
        return Offset(b.centerX, b.centerY)
    }

    /** Закрывает паузу серии колеса (и таймаут двойного тапа): двигает виртуальное время. */
    private fun ComposeUiTest.settleGestures() {
        mainClock.advanceTimeBy(400)
        waitForIdle()
    }

    /**
     * Серия тиков над рамкой — ровно один undo-шаг: снимок берётся на первом тике,
     * checkpoint — на END по паузе. Если бы шагом был тик, один Undo вернул бы рамку лишь
     * на одну ступень (1600 * 1.1^-1) и undo остался бы доступен.
     */
    @Test
    fun wheelSeriesOnFrameZoomsItAsASingleUndoStep() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(listOf("p1"), repo)
        setBuilderContent(vm, repo)
        val before = vm.state.value.items.single().border()
        assertFalse("предусловие: undo-пусто", vm.state.value.canUndo)

        val c = centerOf(vm.state.value, 0)
        val at = local(vm.state.value, c.x, c.y)
        // Три тика в ОДНОМ блоке — одна серия: между тиками паузы нет.
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(at)
            scroll(-1f)
            scroll(-1f)
            scroll(-1f)
        }
        settleGestures()

        val zoomed = vm.state.value.items.single().border()
        assertTrue(
            "вверх по колесу должен увеличивать рамку: ${zoomed.width}",
            zoomed.width > before.width,
        )
        assertTrue("серия должна закрыться END-ом и дать undo-шаг", vm.state.value.canUndo)

        vm.onEvent(BuilderEvent.Undo)
        waitForIdle()

        assertEquals(
            "один Undo должен вернуть рамку к исходному размеру",
            before.width,
            vm.state.value.items.single().border().width,
            0.001f,
        )
        assertFalse(vm.state.value.canUndo)
    }

    /** Колесо над невыделенной рамкой выделяет её и масштабирует именно её. */
    @Test
    fun wheelOverUnselectedFrameSelectsAndZoomsThatFrame() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm, repo)
        val firstId = vm.state.value.items[0].id
        val secondId = vm.state.value.items[1].id
        assertEquals("New combo выделяет первую рамку", firstId, vm.state.value.selectedItemId)
        val before0 = vm.state.value.items[0].border()
        val before1 = vm.state.value.items[1].border()

        val c = centerOf(vm.state.value, 1)
        val at = local(vm.state.value, c.x, c.y)
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(at)
            scroll(-1f)
            scroll(-1f)
        }
        settleGestures()

        val after = vm.state.value
        assertEquals("колесо по чужой рамке должно её выделить", secondId, after.selectedItemId)
        assertTrue(
            "рамка под курсором должна увеличиться: ${after.items[1].border().width}",
            after.items[1].border().width > before1.width,
        )
        assertEquals(
            "соседняя рамка не должна меняться",
            before0.width,
            after.items[0].border().width,
            0.001f,
        )
    }

    /** Колесо по пустому месту зумит вид, не трогая рамки, и не входит в undo. */
    @Test
    fun wheelOnEmptySpaceZoomsViewportAndLeavesNoUndoStep() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(listOf("p1"), repo)
        setBuilderContent(vm, repo)
        val before = vm.state.value.items.single().border()
        // Одна рамка на весь холст, значит над ней (полоса letterbox) — пустое место.
        val emptySpot = emptySpotLocal(vm.state.value)

        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(emptySpot)
            scroll(-1f)
            scroll(-1f)
        }
        settleGestures()

        assertTrue(
            "колесо по пустому месту должно увеличить вид, а zoom=${vm.state.value.viewport.zoom}",
            vm.state.value.viewport.zoom > 1f,
        )
        assertFalse("вид не входит в undo: шага быть не должно", vm.state.value.canUndo)
        val after = vm.state.value.items.single().border()
        assertEquals("рамка не должна двигаться", before.centerX, after.centerX, 0.001f)
        assertEquals("рамка не должна двигаться", before.centerY, after.centerY, 0.001f)
        assertEquals("размер рамки не должен меняться", before.width, after.width, 0.001f)
    }

    /** Кнопка «Fit view» возвращает zoom = 1 и нулевой сдвиг вида. */
    @Test
    fun fitViewButtonResetsTheViewport() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(listOf("p1"), repo)
        setBuilderContent(vm, repo)
        val emptySpot = emptySpotLocal(vm.state.value)

        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(emptySpot)
            scroll(-1f)
            scroll(-1f)
        }
        settleGestures()
        assertTrue("предусловие: вид увеличен", vm.state.value.viewport.zoom > 1f)

        // performSemanticsAction, а не performClick: тулбар горизонтально прокручиваемый,
        // и «Fit view» лежит за краем сцены — координатный клик попал бы мимо кнопки.
        onNodeWithTag(TOOL_TAG_FIT_VIEW).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals(1f, vm.state.value.viewport.zoom, 0f)
        assertEquals(Offset.Zero, vm.state.value.viewport.pan)
    }
}
