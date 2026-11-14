package com.combo.feature.builder

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import com.combo.ImageFixtures
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.domain.BitmapSource
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.EmptyThumbnailSource
import com.combo.ui.StubBitmapSource
import com.combo.ui.TestScreen
import com.combo.ui.UiTestBase
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ComboBuilderGesturesTest : UiTestBase() {

    private var nextId = 0

    private fun builder(
        photoIds: List<String>,
        repo: FakePhotoRepository,
        comboRepo: FakeComboRepository = FakeComboRepository(),
        comboId: String? = null,
        bitmapSource: BitmapSource = StubBitmapSource(),
    ): ComboBuilderViewModel =
        ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("photos" to photoIds.joinToString(","), "comboId" to comboId)),
            comboRepo = comboRepo,
            photoRepo = repo,
            bitmapSource = bitmapSource,
            thumbnailSource = EmptyThumbnailSource,
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = ioDispatcher,
        )

    private fun ComposeUiTest.setBuilderContent(
        vm: ComboBuilderViewModel,
        visible: () -> Boolean = { true },
    ) {
        setContent {
            TestScreen(backDispatcher) {
                if (visible()) ComboBuilderScreen(onBack = {}, vm = vm)
            }
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

    private fun ComposeUiTest.canvasPointer(
        type: PointerEventType,
        at: Offset,
        time: Long,
        shift: Boolean = false,
    ) {
        val node = onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode()
        val root = node.root as PlatformRootForTest
        val position = node.positionInRoot + at
        runOnUiThread {
            root.sendPointerEvent(
                type, position, timeMillis = time,
                buttons = PointerButtons(isPrimaryPressed = type != PointerEventType.Release),
                keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = shift),
                button = if (type == PointerEventType.Move) null else PointerButton(0),
            )
        }
    }

    private fun ComposeUiTest.canvasClick(at: Offset, time: Long) {
        canvasPointer(PointerEventType.Press, at, time)
        canvasPointer(PointerEventType.Release, at, time + 16)
    }

    @Test
    fun frameMoveShowsGuidesOnlyOnCaptureAndReleasesBothWaysOrOnCtrl() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val combo = ComboWithItems(comboEntity("combo"), listOf(
            comboItemEntity("moving", "combo", zIndex = 0, photoId = "p1").copy(
                centerX = 400f, centerY = 620f, width = 200f, height = 200f,
            ),
            comboItemEntity("target", "combo", zIndex = 1, photoId = "p2").copy(
                centerX = 760f, centerY = 280f, width = 200f, height = 200f,
            ),
        ))
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int) =
                ImageFixtures.solidBitmap(32, 22, if (photoId == "p1") 0xFF604C79.toInt() else 0xFF315B70.toInt())
        }
        val vm = builder(emptyList(), repo, FakeComboRepository(listOf(combo)), "combo", source)
        setBuilderContent(vm)
        waitUntil { vm.bitmaps.value.keys.containsAll(listOf("p1", "p2")) }
        assertFalse(vm.state.value.isDirty)

        val st = vm.state.value
        val scale = st.viewport.scale(Size(st.canvasW, st.canvasH), Size(st.screenW, st.screenH))
        val start = local(st, 400f, 620f)
        val aligned = local(st, 760f, 620f)
        val canvas = onNodeWithTag(EDITOR_CANVAS_TAG)
        val previews = File("build/ui-previews").apply { mkdirs() }
        fun movingX() = vm.state.value.items.single { it.id == "moving" }.centerX
        fun move(pixelsFromGuide: Float, time: Long) {
            canvasPointer(PointerEventType.Move, aligned + Offset(pixelsFromGuide, 0f), time)
            waitForIdle()
        }

        canvasPointer(PointerEventType.Press, start, time = 1_000L)
        assertTrue(vm.state.value.snapGuides.isEmpty())
        assertEquals("moving", vm.state.value.items.last().id)
        move(-10f, 1_016L)
        assertEquals(760f - 10f / scale, movingX(), 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())

        move(-3f, 1_032L)
        assertEquals(760f, movingX(), 0.001f)
        assertEquals(660f, vm.state.value.snapGuides.single().coordinate, 0.001f)
        File(previews, "builder-guide-active.png").writeBytes(ImageFixtures.pngBytes(canvas.captureToImage()))
        move(10f, 1_048L)
        assertEquals(760f + 10f / scale, movingX(), 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())
        move(3f, 1_064L)
        assertEquals(760f, movingX(), 0.001f)
        move(-10f, 1_080L)
        assertEquals(760f - 10f / scale, movingX(), 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())

        move(-3f, 1_096L)
        assertEquals(760f, movingX(), 0.001f)
        canvas.performKeyInput { keyDown(Key.CtrlLeft) }
        assertEquals(760f - 3f / scale, movingX(), 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())
        canvas.performKeyInput { keyUp(Key.CtrlLeft) }
        canvasPointer(PointerEventType.Release, aligned + Offset(-3f, 0f), time = 1_112L)
        assertEquals(760f - 3f / scale, movingX(), 0.001f)
        assertTrue(vm.state.value.canUndo)
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(400f, movingX(), 0.001f)
        assertEquals(listOf("moving", "target"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun photoPanSnapsItsOwnEdgeAndCtrlRestoresTheFreePosition() = runComposeUiTest {
        val photo = photoEntity("p1")
        val repo = FakePhotoRepository(listOf(photo))
        val moving = comboItemEntity("moving", "combo").copy(
            centerX = 600f, centerY = 450f, width = 400f, height = 320f, contentScale = 0.5f,
        )
        val combo = ComboWithItems(comboEntity("combo"), listOf(moving))
        val vm = builder(emptyList(), repo, FakeComboRepository(listOf(combo)), "combo")
        setBuilderContent(vm)
        selectPhotoMode(vm, moving.id)
        val st = vm.state.value
        val scale = st.viewport.scale(Size(st.canvasW, st.canvasH), Size(st.screenW, st.screenH))
        val original = st.items.single()
        val frame = original.border()
        val originalPan = Offset(original.panX, original.panY)
        val contentScale = original.contentScale
        val snapPan = (photo.width * contentScale - frame.width) / 2f
        val start = local(st, frame.centerX, frame.centerY)
        val near = start + Offset(snapPan * scale - 3f, 0f)
        val canvas = onNodeWithTag(EDITOR_CANVAS_TAG)

        canvasPointer(PointerEventType.Press, start, time = 1_000L)
        canvasPointer(PointerEventType.Move, near, time = 1_016L)
        waitForIdle()
        val held = vm.state.value.items.single()
        assertEquals(snapPan, held.panX, 0.001f)
        assertEquals(originalPan.y, held.panY, 0f)
        assertEquals(frame, held.border())
        assertEquals(contentScale, held.contentScale, 0f)
        assertEquals(frame.rect().left, vm.state.value.snapGuides.single().coordinate, 0.001f)

        canvas.performKeyInput { keyDown(Key.CtrlLeft) }
        assertEquals(snapPan - 3f / scale, vm.state.value.items.single().panX, 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())
        canvasPointer(PointerEventType.Release, near, time = 1_032L)
        canvas.performKeyInput { keyUp(Key.CtrlLeft) }
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(originalPan.x, vm.state.value.items.single().panX, 0f)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun shiftResizeKeepsOppositeEdgeFixedThroughSnapReleaseAndUndo() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val combo = ComboWithItems(comboEntity("combo"), listOf(
            comboItemEntity("moving", "combo", zIndex = 0, photoId = "p1").copy(
                centerX = 400f, centerY = 450f, width = 200f, height = 200f,
            ),
            comboItemEntity("target", "combo", zIndex = 1, photoId = "p2").copy(
                centerX = 650f, centerY = 250f, width = 100f, height = 120f,
            ),
        ))
        val vm = builder(emptyList(), repo, FakeComboRepository(listOf(combo)), "combo")
        setBuilderContent(vm)
        selectPhotoMode(vm, "moving")
        val st = vm.state.value
        val scale = st.viewport.scale(Size(st.canvasW, st.canvasH), Size(st.screenW, st.screenH))
        val start = local(st, 500f, 450f)
        val aligned = local(st, 600f, 450f)
        fun moving() = vm.state.value.items.single { it.id == "moving" }.border().rect()
        fun move(pixelsFromGuide: Float, time: Long) {
            canvasPointer(PointerEventType.Move, aligned + Offset(pixelsFromGuide, 0f), time)
            waitForIdle()
            assertEquals(300f, moving().left, 0.001f)
        }

        canvasPointer(PointerEventType.Press, start, time = 1_000L, shift = true)
        move(-3f, 1_016L)
        assertEquals(600f, moving().right, 0.001f)
        assertEquals(600f, vm.state.value.snapGuides.single().coordinate, 0.001f)
        move(10f, 1_032L)
        assertEquals(600f + 10f / scale, moving().right, 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())
        move(3f, 1_048L)
        assertEquals(600f, moving().right, 0.001f)
        move(-10f, 1_064L)
        assertEquals(600f - 10f / scale, moving().right, 0.001f)
        move(-3f, 1_080L)
        canvasPointer(PointerEventType.Release, aligned + Offset(-3f, 0f), time = 1_096L)
        assertEquals(600f, moving().right, 0.001f)
        assertTrue(vm.state.value.snapGuides.isEmpty())
        assertTrue(vm.state.value.canUndo)
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(500f, moving().right, 0.001f)
        assertEquals(300f, moving().left, 0.001f)
        assertFalse(vm.state.value.canUndo)
    }

    private fun ComposeUiTest.selectPhotoMode(vm: ComboBuilderViewModel, itemId: String) {
        runOnUiThread {
            vm.onEvent(BuilderEvent.ItemTapped(itemId))
            vm.onEvent(BuilderEvent.ToggleMode)
        }
        waitForIdle()
        assertEquals(EditMode.BORDER, vm.state.value.mode)
    }

    @Test
    fun shiftClickSelectsInPlaceAndClickImmediatelyRaisesAndSavesTheLayer() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2"), photoEntity("p3")))
        val comboRepo = FakeComboRepository(listOf(ComboWithItems(
            comboEntity("combo"),
            (0..2).map { index ->
                comboItemEntity("frame-$index", "combo", zIndex = index, photoId = "p${index + 1}").copy(
                    centerX = 400f + 400f * index, width = 600f, height = 450f,
                )
            },
        )))
        val allowSave = CompletableDeferred<Unit>()
        comboRepo.saveGate = { allowSave.await() }
        val vm = builder(emptyList(), repo, comboRepo, comboId = "combo")
        setBuilderContent(vm)
        val initial = vm.state.value.items.map { it.id }
        val exposed = local(vm.state.value, 400f, 450f)
        val overlap = local(vm.state.value, 600f, 450f)

        canvasPointer(PointerEventType.Press, exposed, time = 1_000L, shift = true)
        assertEquals(initial.first(), vm.state.value.selectedItemId)
        assertEquals(initial, vm.state.value.items.map { it.id })
        canvasPointer(PointerEventType.Release, exposed, time = 1_016L)
        assertFalse(vm.state.value.isDirty)
        assertFalse(vm.state.value.canUndo)

        canvasPointer(PointerEventType.Press, exposed, time = 2_000L)
        val raised = initial.drop(1) + initial.first()
        assertEquals(raised, vm.state.value.items.map { it.id })
        assertEquals(listOf(0, 1, 2), vm.state.value.items.map { it.zIndex })
        assertFalse(vm.state.value.isDirty)
        assertFalse(vm.state.value.canUndo)
        canvasPointer(PointerEventType.Release, exposed, time = 2_016L)
        assertTrue(vm.state.value.isDirty)
        assertTrue(vm.state.value.canUndo)

        canvasClick(overlap, time = 3_000L)
        assertEquals(initial.first(), vm.state.value.selectedItemId)
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(initial, vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.canUndo)
        runOnUiThread {
            vm.onEvent(BuilderEvent.Redo)
            vm.onEvent(BuilderEvent.Save)
        }
        try {
            assertTrue(vm.state.value.saving)
            canvasClick(local(vm.state.value, 1_350f, 450f), time = 4_000L)
            assertEquals(raised, vm.state.value.items.map { it.id })
        } finally {
            allowSave.complete(Unit)
        }
        waitUntil(timeoutMillis = 5_000) { comboRepo.savedCombos.isNotEmpty() }
        assertEquals(raised, comboRepo.savedCombos.single().second.map { it.id })
        assertEquals(listOf(0, 1, 2), comboRepo.savedCombos.single().second.map { it.zIndex })
    }

    @Test
    fun pressAndDragMovesTheRaisedFrameWithOneUndoStep() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)
        val st = vm.state.value
        val initial = st.items.associate { it.id to it.border() }
        val first = st.items.first().id
        val second = st.items.last().id
        val from = local(st, 640f, 450f)
        val to = local(st, 960f, 450f)

        canvasPointer(PointerEventType.Press, from, time = 1_000L)
        assertEquals(first, vm.state.value.selectedItemId)
        assertEquals(listOf(second, first), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.canUndo)

        canvasPointer(PointerEventType.Move, to, time = 1_016L)
        assertTrue(vm.state.value.items.last().border().centerX > initial.getValue(first).centerX)
        assertEquals(initial.getValue(second), vm.state.value.items.first().border())
        assertFalse(vm.state.value.canUndo)
        canvasPointer(PointerEventType.Release, to, time = 1_032L)
        assertTrue(vm.state.value.canUndo)

        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(initial.keys.toList(), vm.state.value.items.map { it.id })
        assertEquals(initial, vm.state.value.items.associate { it.id to it.border() })
        assertFalse(vm.state.value.canUndo)

        // Shift applies at press time. Releasing Shift while dragging keeps the layer in place.
        canvasPointer(PointerEventType.Press, from, time = 2_000L, shift = true)
        canvasPointer(PointerEventType.Move, to, time = 2_016L)
        assertEquals(initial.keys.toList(), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.items.first().border().centerX > initial.getValue(first).centerX)
        canvasPointer(PointerEventType.Release, to, time = 2_032L)
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun removingTheCanvasDuringDragRollsBackThePendingLayerAndMovement() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        val visible = mutableStateOf(true)
        setBuilderContent(vm) { visible.value }
        val st = vm.state.value
        val initial = st.items.associate { it.id to it.border() }
        val center = centerOf(st, 0)
        val at = local(st, center.x, center.y)

        canvasPointer(PointerEventType.Press, at, time = 1_000L)
        assertEquals(initial.keys.reversed(), vm.state.value.items.map { it.id })
        canvasPointer(PointerEventType.Move, at + Offset(80f, 0f), time = 1_016L)
        assertTrue(vm.state.value.items.last().border().centerX > center.x)
        runOnUiThread { visible.value = false }
        waitForIdle()

        assertEquals(initial.keys.toList(), vm.state.value.items.map { it.id })
        assertEquals(initial, vm.state.value.items.associate { it.id to it.border() })
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun doubleClickRaisesTheFrameAndTogglesModeWithOneLayerUndoStep() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)
        val st = vm.state.value
        val initial = st.items.map { it.id }
        val at = centerOf(st, 0)

        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput { doubleClick(local(st, at.x, at.y)) }

        assertEquals(EditMode.BORDER, vm.state.value.mode)
        assertEquals(initial.reversed(), vm.state.value.items.map { it.id })
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(initial, vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun clickAfterWheelKeepsSeparateUndoStepsForTheResizeAndLayerOrder() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)
        val st = vm.state.value
        val initial = st.items.map { it.id }
        val width = st.items.first().border().width
        val center = centerOf(st, 0)
        val at = local(st, center.x, center.y)

        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(at)
            scroll(-1f)
            click(at)
        }

        val resized = vm.state.value.items.last().border().width
        assertTrue(resized > width)
        assertEquals(initial.reversed(), vm.state.value.items.map { it.id })
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(initial, vm.state.value.items.map { it.id })
        assertEquals(resized, vm.state.value.items.first().border().width, 0.001f)
        runOnUiThread { vm.onEvent(BuilderEvent.Undo) }
        assertEquals(width, vm.state.value.items.first().border().width, 0.001f)
        assertFalse(vm.state.value.canUndo)
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
        setBuilderContent(vm)
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
        setBuilderContent(vm)

        vm.onEvent(BuilderEvent.ToggleMode)
        waitForIdle()
        assertEquals(EditMode.BORDER, vm.state.value.mode)

        // Первый ресайз — левая грань первой рамки (канвасная координата 0).
        val resizedId = vm.state.value.items.first().id
        val left = vm.state.value.items.first().border()
        dragEdge(
            vm.state.value,
            Offset(left.centerX - left.width / 2, left.centerY),
            Offset(-80f, 0f),
        )
        val afterFirst = vm.state.value.items.single { it.id == resizedId }.border()
        assertTrue("ресайз за левую грань не сработал", afterFirst.width > left.width + 50f)

        // Повторный ресайз — правая грань на её НОВОМ месте. До фикса проба грани
        // сравнивала несмасштабированный прямоугольник с экранной точкой: мимо
        // грани — и жест вырождался в сдвиг, размер не менялся.
        dragEdge(
            vm.state.value,
            Offset(afterFirst.centerX + afterFirst.width / 2, afterFirst.centerY),
            Offset(80f, 0f),
        )
        val afterSecond = vm.state.value.items.single { it.id == resizedId }.border().width
        assertTrue("повторный ресайз за правую грань не сработал", afterSecond > afterFirst.width + 50f)
    }

    // ---------- клик с микросдвигом ----------

    @Test
    fun tapWithTinyJitterStillSelectsFrame() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)
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
        setBuilderContent(vm)
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
        setBuilderContent(vm)

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
