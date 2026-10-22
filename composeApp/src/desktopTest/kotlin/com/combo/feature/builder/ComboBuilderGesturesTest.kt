package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Жесты холста редактора мышью: перетаскивание рамок, клик с микросдвигом, клик после
 * сдвига вида.
 *
 * Координаты ввода — ЛОКАЛЬНЫЕ для узла холста (так их принимает `performMouseInput`) и
 * считаются по тем же формулам, что и сам холст редактора, поэтому тест не дублирует
 * отрисовку, а проверяет только геометрию ввода.
 *
 * Многопальцевые сценарии касаний (второй палец посреди жеста, медленное разведение
 * двумя пальцами) мышью не воспроизводятся: их место в desktop-вводе занимает колесо,
 * и его сценарии проверяет [EditorWheelTest].
 */
@OptIn(ExperimentalTestApi::class)
class ComboBuilderGesturesTest : UiTestBase() {

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

    /**
     * Разбор тапа в detectTapGestures ждёт истечения таймаута двойного тапа, а колесо —
     * паузы серии; в skiko-тестах это виртуальное время двигает только mainClock.
     */
    private fun ComposeUiTest.settleGestures() {
        mainClock.advanceTimeBy(400)
        waitForIdle()
    }

    private fun ComposeUiTest.tap(at: Offset) {
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(at)
            press()
            release()
        }
        settleGestures()
    }

    /** Ресайз за грань: пресс точно на грани, протяжка половинами, release. */
    private fun ComposeUiTest.dragEdge(st: BuilderUiState, at: Offset, by: Offset) {
        val from = local(st, at.x, at.y)
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(from)
            press()
            moveBy(by / 2f)
            moveBy(by / 2f)
            release()
        }
        settleGestures()
    }

    // ---------- перетаскивание невыделенной рамки при выделенной другой ----------

    @Test
    fun draggingUnselectedFrameMovesThatFrameNotTheSelectedOne() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm, repo)
        assertEquals(2, vm.state.value.items.size)

        val firstId = vm.state.value.items[0].id
        // New combo выделяет первую рамку — тянем вторую.
        assertEquals(firstId, vm.state.value.selectedItemId)
        val before0 = vm.state.value.items[0].border()
        val before1 = vm.state.value.items[1].border()
        val undoBefore = vm.state.value.canUndo

        val second = centerOf(vm.state.value, 1)
        val from = local(vm.state.value, second.x, second.y)
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(from)
            press()
            moveBy(Offset(0f, 120f))
            moveBy(Offset(0f, 120f))
            release()
        }
        settleGestures()

        val after = vm.state.value
        assertEquals(
            "выделенная рамка не должна двигаться",
            before0.centerY,
            after.items[0].border().centerY,
            0.001f,
        )
        // Если pointerInput перезапускается при смене выделения, жест обрывается на
        // ItemTapped: рамка не двигается, isDirty и undo-шаг не появляются.
        assertTrue(
            "перетаскиваемая рамка должна сдвинуться",
            after.items[1].border().centerY > before1.centerY + 1f,
        )
        assertTrue("перетаскивание должно завершиться END-ом (isDirty)", after.isDirty)
        assertTrue(
            "должен появиться undo-шаг (до жеста его не было: $undoBefore)",
            !undoBefore && after.canUndo,
        )
    }

    // ---------- изменение размера за грань ----------

    @Test
    fun resizingBorderKeepsWorkingOnRepeatedDrags() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm, repo)

        vm.onEvent(BuilderEvent.ToggleMode)
        waitForIdle()
        assertEquals(EditMode.BORDER, vm.state.value.mode)

        // Первый ресайз — левая грань первой рамки (канвасная координата 0).
        val left = vm.state.value.items[0].border()
        dragEdge(
            vm.state.value,
            Offset(left.centerX - left.width / 2, left.centerY),
            Offset(-80f, 0f),
        )
        val afterFirst = vm.state.value.items[0].border().width
        assertTrue("ресайз за левую грань не сработал", afterFirst > left.width + 50f)

        // Повторный ресайз — правая грань на её НОВОМ месте. До фикса проба грани
        // сравнивала несмасштабированный прямоугольник с экранной точкой: мимо
        // грани — и жест вырождался в сдвиг, размер не менялся.
        val right = vm.state.value.items[0].border()
        dragEdge(
            vm.state.value,
            Offset(right.centerX + right.width / 2, right.centerY),
            Offset(80f, 0f),
        )
        val afterSecond = vm.state.value.items[0].border().width
        assertTrue("повторный ресайз за правую грань не сработал", afterSecond > afterFirst + 50f)
    }

    // ---------- клик с микросдвигом ----------

    @Test
    fun tapWithTinyJitterStillSelectsFrame() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm, repo)
        val firstId = vm.state.value.items[0].id
        val secondId = vm.state.value.items[1].id
        assertEquals("New combo выделяет первую рамку", firstId, vm.state.value.selectedItemId)
        val c = centerOf(vm.state.value, 1)
        val at = local(vm.state.value, c.x, c.y)

        // Если событие consumed до выхода за slop, detectTapGestures отменяет тап.
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(at)
            press()
            moveBy(Offset(2f, 2f))
            release()
        }
        settleGestures()

        assertEquals(secondId, vm.state.value.selectedItemId)
    }

    @Test
    fun dragBeyondSlopCancelsTap() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm, repo)
        val firstId = vm.state.value.items[0].id
        assertEquals("New combo выделяет первую рамку", firstId, vm.state.value.selectedItemId)

        // Тянем за slop по пустому месту. Это PAN_VIEWPORT: тап по рамке при этом
        // отменяется, поэтому выделение первой рамки не должно сброситься в null
        // (иначе тап «просочился» бы мимо отмены по slop).
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(emptySpotLocal(vm.state.value))
            press()
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            release()
        }
        settleGestures()

        assertTrue("жест должен был стать PAN_VIEWPORT", vm.state.value.viewport.pan.y > 10f)
        assertEquals(
            "тап должен отменяться выходом за slop",
            firstId,
            vm.state.value.selectedItemId,
        )
    }

    // ---------- сдвиг вида не должен ломать последующие клики ----------

    @Test
    fun tapAfterViewportPanHitsFrameAtNewPosition() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm, repo)

        // Тянем по пустому месту (полоса letterbox) — это PAN_VIEWPORT.
        // Центр рамки после сдвига остаётся в доступной области независимо от панелей.
        val panStep = vm.state.value.screenH * 0.225f
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(emptySpotLocal(vm.state.value))
            press()
            moveBy(Offset(0f, panStep))
            moveBy(Offset(0f, panStep))
            release()
        }
        settleGestures()
        assertTrue("сдвиг вида не применился", vm.state.value.viewport.pan.y > 50f)

        // Кликаем вторую рамку по её НОВОМУ положению.
        val st = vm.state.value
        val c = centerOf(st, 1)
        tap(local(st, c.x, c.y))

        // Со старым (устаревшим) origin клик промахивался мимо рамки -> CanvasTapped -> null.
        assertEquals(
            "клик должен попасть в рамку после сдвига вида",
            st.items[1].id,
            vm.state.value.selectedItemId,
        )
    }
}
