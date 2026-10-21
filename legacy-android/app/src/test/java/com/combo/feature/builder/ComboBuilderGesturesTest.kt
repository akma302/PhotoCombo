package com.combo.feature.builder

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.ui.FakeBitmapSource
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.photoEntity
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Жесты на холсте редактора (Compose UI-тесты под Robolectric): перетаскивание рамок,
 * тап с микросдвигом, тап после сдвига вида, второй палец посреди жеста, разведение двумя
 * пальцами.
 *
 * Координаты ввода считаются по тем же формулам, что и сам холст редактора (масштаб и
 * начало координат холста на экране), поэтому тест не дублирует отрисовку, а проверяет
 * только геометрию ввода.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComboBuilderGesturesTest {

    /** Параметры «медленного щипка»: шаг разведения на палец, их число и стартовый размах. */
    private val PINCH_STEP_PX = 8f
    private val PINCH_STEPS = 4
    private val PINCH_START_SPAN_PX = 20f

    @get:Rule
    val compose = createComposeRule()

    private fun vm(photoIds: List<String>): ComboBuilderViewModel =
        ComboBuilderViewModel(
            SavedStateHandle(mapOf("photos" to photoIds.joinToString(","))),
            FakeComboRepository(),
            FakePhotoRepository(photoIds.map { photoEntity(it) }),
            FakeBitmapSource(),
        )

    private fun setContent(vm: ComboBuilderViewModel) {
        compose.setContent { ComboBuilderScreen(onBack = {}, vm = vm) }
        compose.waitUntil(timeoutMillis = 5_000) {
            vm.state.value.loaded && vm.state.value.screenW > 0f
        }
    }

    private fun canvasBounds(): Rect =
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode().boundsInRoot

    /** Экранная точка для координаты холста — та же формула, что в EditorViewport. */
    private fun screen(st: BuilderUiState, bounds: Rect, x: Float, y: Float): Offset {
        val vs = st.viewport.scale(
            Size(st.canvasW, st.canvasH),
            Size(st.screenW, st.screenH),
        )
        val originX = (st.screenW - st.canvasW * vs) / 2 + st.viewport.pan.x
        val originY = (st.screenH - st.canvasH * vs) / 2 + st.viewport.pan.y
        return Offset(bounds.left + originX + x * vs, bounds.top + originY + y * vs)
    }

    private fun centerOf(st: BuilderUiState, index: Int): Offset {
        val b = st.items[index].border()
        return Offset(b.centerX, b.centerY)
    }

    /**
     * detectTapGestures сначала ЖДЁТ истечения long-press таймаута и только потом
     * разбирает тап. На реальном устройстве это незаметно, в Robolectric часы стоят,
     * поэтому тап «съедался» (срабатывал longPress). Отсюда ручной прогон часов.
     */
    private fun settleGestures() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        compose.waitForIdle()
    }

    private fun tap(at: Offset) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(at)
            up()
        }
        settleGestures()
    }

    // ---------- перетаскивание невыделенной рамки при выделенной другой ----------

    @Test
    fun draggingUnselectedFrameMovesThatFrameNotTheSelectedOne() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)
        assertEquals(2, vm.state.value.items.size)
        val bounds = canvasBounds()

        val firstId = vm.state.value.items[0].id
        // New combo выделяет первую рамку — тянем вторую.
        assertEquals(firstId, vm.state.value.selectedItemId)
        vm.onEvent(BuilderEvent.Undo) // чистый старт: без предыдущих шагов
        vm.onEvent(BuilderEvent.ItemTapped(firstId))
        val before0 = vm.state.value.items[0].border()
        val before1 = vm.state.value.items[1].border()
        val undoBefore = vm.state.value.canUndo

        val second = centerOf(vm.state.value, 1)
        val from = screen(vm.state.value, bounds, second.x, second.y)
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(from)
            moveBy(Offset(0f, 120f))
            moveBy(Offset(0f, 120f))
            up()
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

    // ---------- тап с микросдвигом ----------

    @Test
    fun tapWithTinyJitterStillSelectsFrame() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)
        val bounds = canvasBounds()
        val firstId = vm.state.value.items[0].id
        val secondId = vm.state.value.items[1].id
        assertEquals("New combo выделяет первую рамку", firstId, vm.state.value.selectedItemId)
        val c = centerOf(vm.state.value, 1)
        val at = screen(vm.state.value, bounds, c.x, c.y)

        // Если событие consumed до выхода за slop, detectTapGestures отменяет тап.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(at)
            moveBy(Offset(2f, 2f))
            up()
        }
        settleGestures()

        assertEquals(secondId, vm.state.value.selectedItemId)
    }

    @Test
    fun dragBeyondSlopCancelsTap() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)
        val bounds = canvasBounds()
        val firstId = vm.state.value.items[0].id
        assertEquals("New combo выделяет первую рамку", firstId, vm.state.value.selectedItemId)

        // Тянем за slop по пустому месту. Это PAN_VIEWPORT: тап по рамке при этом
        // отменяется, поэтому выделение первой рамки не должно сброситься в null
        // (иначе тап «просочился» бы мимо отмены по slop).
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, Offset(bounds.center.x, bounds.top + 8f))
            moveBy(0, Offset(0f, 40f))
            moveBy(0, Offset(0f, 40f))
            up()
        }
        settleGestures()

        assertTrue("жест должен был стать PAN_VIEWPORT", vm.state.value.viewport.pan.y > 10f)
        assertEquals(
            "тап должен отменяться выходом за slop",
            firstId,
            vm.state.value.selectedItemId,
        )
    }

    // Двойной тап (onDoubleTap -> ToggleMode) автотестом не покрыт: detectTapGestures
    // держит разбор тапа на таймере long-press, и под Robolectric пришлось бы останавливать
    // часы теста, из-за чего UP первого тапа перетирается DOWN второго.
    // Ручная проверка: New combo -> два быстрых тапа по рамке -> режим переключается
    // между EDIT и BORDER.

    // ---------- сдвиг вида не должен ломать последующие тапы ----------

    @Test
    fun tapAfterViewportPanHitsFrameAtNewPosition() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)
        val bounds = canvasBounds()

        // Тянем по пустому месту (полоса letterbox сверху) — это PAN_VIEWPORT.
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, Offset(bounds.center.x, bounds.top + 8f))
            moveBy(0, Offset(0f, 150f))
            moveBy(0, Offset(0f, 150f))
            up()
        }
        settleGestures()
        assertTrue("сдвиг вида не применился", vm.state.value.viewport.pan.y > 50f)

        // Тапаем вторую рамку по её НОВОМУ положению.
        val st = vm.state.value
        val c = centerOf(st, 1)
        tap(screen(st, bounds, c.x, c.y))

        // Со старым (устаревшим) origin тап промахивался мимо рамки -> CanvasTapped -> null.
        assertEquals(
            "тап должен попасть в рамку после сдвига вида",
            st.items[1].id,
            vm.state.value.selectedItemId,
        )
    }

    // ---------- появление второго пальца не должно дёргать рамку ----------

    @Test
    fun secondPointerDoesNotJumpFrame() {
        val vm = vm(listOf("p1"))
        setContent(vm)
        val bounds = canvasBounds()
        val c = centerOf(vm.state.value, 0)
        val at = screen(vm.state.value, bounds, c.x, c.y)
        val start = vm.state.value.items.single().border()

        // Второй палец кладём далеко и отпускаем ПЕРВЫМ: смена числа пальцев не должна
        // приводить к «телепортации» рамки (до фикса центроид прыгал на обоих событиях).
        val secondFinger = Offset(bounds.left + 6f, bounds.top + 6f)
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, at)
            moveBy(0, Offset(40f, 0f)) // slop пройден, DRAG_ITEM стартует: +40px
            down(1, secondFinger)      // 1 -> 2 пальца: дельта пропускается
            moveBy(0, Offset(40f, 0f)) // центроид смещается на 20px
            up(1)                       // 2 -> 1 палец: дельта тоже пропускается
            up(0)
        }
        settleGestures()

        val moved = vm.state.value.items.single().border()
        val vs = vm.currentVs()
        // Ожидаем ровно 40px (первый drag) + 20px (общий центроид двух пальцев) = 60px.
        val expected = 60f / vs
        val actual = moved.centerX - start.centerX
        assertEquals("рамка должна сдвинуться ровно на дельту пальцев", expected, actual, 2f)
    }

    /**
     * Медленное симметричное разведение двумя пальцами тоже должно распознаваться.
     *
     * Каждый палец уезжает на `PINCH_STEP_PX * PINCH_STEPS` = 32 px, то есть размах растёт
     * на 2×32 = 64 px — шаг за шагом меньше touchSlop, и жест на этом не начинается
     * (одного шага хватает только на смену опорной точки, если бы она не была
     * неизменной). Распознаётся он на последнем шаге, и один щипок применяет отношение
     * текущего размаха к предыдущему — так что рамка обязана вырасти заметно сильнее, чем
     * даёт разведение на один шаг.
     */
    @Test
    fun slowSymmetricPinchIsRecognized() {
        val vm = vm(listOf("p1"))
        setContent(vm)
        val bounds = canvasBounds()
        val c = centerOf(vm.state.value, 0)
        val at = screen(vm.state.value, bounds, c.x, c.y)
        val start = vm.state.value.items.single().border()
        val vs = vm.currentVs()

        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, at)
            down(1, Offset(at.x + PINCH_START_SPAN_PX, at.y))
            repeat(PINCH_STEPS) {
                moveBy(0, Offset(-PINCH_STEP_PX, 0f))
                moveBy(1, Offset(PINCH_STEP_PX, 0f))
            }
            up(1)
            up(0)
        }
        settleGestures()

        val moved = vm.state.value.items.single().border()
        val oneStepGain = 2f * PINCH_STEP_PX / vs
        val actual = moved.width - start.width
        assertTrue(
            "разведение на ${PINCH_STEP_PX * PINCH_STEPS}px на палец должно увеличить рамку " +
                "больше, чем на один шаг (~$oneStepGain), а было $actual",
            actual > oneStepGain,
        )
    }

    /**
 * E: щипок по ПУСТОМУ месту (PAN_VIEWPORT) зумит вид, а не рамку.
 * Именно поэтому `ViewportZoom` несёт фокус: холст не должен уезжать из-под пальцев.
 */
    @Test
    fun pinchOnEmptySpaceZoomsTheViewportWithoutMovingFrames() {
        val vm = vm(listOf("p1"))
        setContent(vm)
        val bounds = canvasBounds()
        // Одна рамка на весь холст, значит надпись над ней (полоса letterbox) — пустое место.
        val start = vm.state.value.items.single().border()
        val emptySpot = Offset(bounds.center.x, bounds.top + 80f)

        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, Offset(emptySpot.x - PINCH_START_SPAN_PX / 2f, emptySpot.y))
            down(1, Offset(emptySpot.x + PINCH_START_SPAN_PX / 2f, emptySpot.y))
            repeat(PINCH_STEPS) {
                moveBy(0, Offset(-PINCH_STEP_PX, 0f))
                moveBy(1, Offset(PINCH_STEP_PX, 0f))
            }
            up(1)
            up(0)
        }
        settleGestures()

        assertTrue(
            "щипок по пустому месту должен был увеличить вид, а zoom=${vm.state.value.viewport.zoom}",
            vm.state.value.viewport.zoom > 1f,
        )
        val after = vm.state.value.items.single().border()
        assertEquals("рамка не должна двигаться", start.centerX, after.centerX, 0.001f)
        assertEquals("рамка не должна двигаться", start.centerY, after.centerY, 0.001f)
        assertEquals("размер рамки не должен меняться", start.width, after.width, 0.001f)
        assertEquals(start.height, after.height, 0.001f)
    }

    /** E: кнопка «Fit view» возвращает zoom = 1 и нулевой сдвиг вида. */
    @Test
    fun fitViewButtonResetsTheViewport() {
        val vm = vm(listOf("p1"))
        setContent(vm)
        val bounds = canvasBounds()
        val emptySpot = Offset(bounds.center.x, bounds.top + 80f)

        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, Offset(emptySpot.x - PINCH_START_SPAN_PX / 2f, emptySpot.y))
            down(1, Offset(emptySpot.x + PINCH_START_SPAN_PX / 2f, emptySpot.y))
            repeat(PINCH_STEPS) {
                moveBy(0, Offset(-PINCH_STEP_PX, 0f))
                moveBy(1, Offset(PINCH_STEP_PX, 0f))
            }
            up(1)
            up(0)
        }
        settleGestures()
        assertTrue("предусловие: вид увеличен", vm.state.value.viewport.zoom > 1f)

        // performSemanticsAction, а не performClick: тулбар горизонтально прокручиваемый,
        // и «Fit view» лежит за краем экрана — координатный клик попал бы мимо кнопки.
        compose.onNodeWithTag(TOOL_TAG_FIT_VIEW).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(1f, vm.state.value.viewport.zoom, 0f)
        assertEquals(Offset.Zero, vm.state.value.viewport.pan)
    }

    /** B3: касание двумя пальцами без движения — это не жест: ни undo-шага, ни isDirty. */
    @Test
    fun twoFingersWithoutMovementIsNotAGesture() {
        val vm = vm(listOf("p1"))
        setContent(vm)
        val bounds = canvasBounds()
        val c = centerOf(vm.state.value, 0)
        val at = screen(vm.state.value, bounds, c.x, c.y)
        val dirtyBefore = vm.state.value.isDirty
        // Снимаем выделение: если бы жест стартовал, он успел бы вызвать ItemTapped.
        vm.onEvent(BuilderEvent.CanvasTapped)
        compose.waitForIdle()
        assertNull("предусловие: выделение снято", vm.state.value.selectedItemId)
        assertFalse("предусловие: undo-пусто", vm.state.value.canUndo)

        compose.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(0, at)
            down(1, Offset(bounds.left + 6f, bounds.top + 6f))
            up(1)
            up(0)
        }
        settleGestures()

        assertFalse("касание двумя пальцами не должно давать undo-шаг", vm.state.value.canUndo)
        assertEquals(
            "isDirty не должен меняться от жеста без изменений",
            dirtyBefore,
            vm.state.value.isDirty,
        )
        // Про выделение: detectTapGestures (стандартное поведение Compose) считает
        // двухпальцевое касание обычным тапом первого пальца, поэтому выделение может
        // измениться. Это не начало жеста: нет ни ItemDrag, ни undo-шага. Выделение
        // здесь не проверяется: поведение детектора тапов от нас не зависит.
    }
}