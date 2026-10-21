package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.SavedStateHandle
import com.combo.core.render.ItemDraft
import com.combo.core.render.ItemGestures
import com.combo.core.render.Viewport
import com.combo.core.render.viewportZoomPan
import com.combo.feature.builder.BuilderEvent
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.builder.GesturePhase
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
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

/**
 * Зум вида, сброс вида и границы рамок.
 *
 * Зум вида проверяется чистой функцией `viewportZoomPan`: главное свойство — точка холста
 * под центром пальцев остаётся на месте. Оно нарушается при наивном «просто умножить zoom».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewportTest {

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

    private val canvas = Size(1600f, 900f)
    private val screen = Size(1000f, 600f)

    /** Точка холста под экранной точкой `p`: `(p - origin) / vs`. */
    private fun canvasPointUnder(p: Offset, origin: Offset, vs: Float) = Offset(
        (p.x - origin.x) / vs,
        (p.y - origin.y) / vs,
    )

    /**
 * Главное свойство зума вида, проверенное и на увеличение, и на уменьшение: точка холста
 * под центром пальцев остаётся на месте. Именно оно «уезжает» при наивном «просто
 * умножить zoom». Панорама ненулевая — при pan = 0 формула выглядела бы рабочей.
 */
    @Test
    fun `canvas point under the pinch centre does not move`() {
        for (factor in listOf(1.8f, 0.6f)) {
            val zoom = 2.4f
            val vs = minOf(screen.width / canvas.width, screen.height / canvas.height) * zoom
            val pan = Offset(37f, -51f)
            val origin = Offset(
                (screen.width - canvas.width * vs) / 2f + pan.x,
                (screen.height - canvas.height * vs) / 2f + pan.y,
            )
            val focus = Offset(640f, 310f)

            val before = canvasPointUnder(focus, origin, vs)
            val newPan = viewportZoomPan(focus, origin, factor, screen, canvas, vs)
            val vsAfter = vs * factor
            val originAfter = Offset(
                (screen.width - canvas.width * vsAfter) / 2f + newPan.x,
                (screen.height - canvas.height * vsAfter) / 2f + newPan.y,
            )
            val after = canvasPointUnder(focus, originAfter, vsAfter)

            assertEquals("x при factor=$factor", before.x, after.x, 1e-2f)
            assertEquals("y при factor=$factor", before.y, after.y, 1e-2f)
        }
    }

    @Test
    fun `zoom around the canvas centre does not shift the view`() {
        val vs = minOf(screen.width / canvas.width, screen.height / canvas.height) * 3f
        val pan = Offset.Zero
        val origin = Offset(
            (screen.width - canvas.width * vs) / 2f + pan.x,
            (screen.height - canvas.height * vs) / 2f + pan.y,
        )
        // Фокус ровно в центре холста на экране — увеличение симметрично.
        val focus = Offset(origin.x + canvas.width * vs / 2f, origin.y + canvas.height * vs / 2f)

        val newPan = viewportZoomPan(focus, origin, 2f, screen, canvas, vs)

        assertEquals("сдвиг вида при зуме от центра должен остаться нулевым", 0f, newPan.x, 1e-3f)
        assertEquals(0f, newPan.y, 1e-3f)
    }

    // ---------- границы рамок ----------

    @Test
    fun `dragged frame centre is clamped to the canvas`() {
        val item = ItemDraft(
            id = "i", zIndex = 0, centerX = 800f, centerY = 450f,
            width = 100f, height = 100f, contents = mutableListOf(),
        )
        // vs = 1 — дельты экрана равны дельтам холста.
        ItemGestures.dragItem(item, Offset(100_000f, 100_000f), 1f, 1600f, 900f)
        assertEquals("центр X не должен уйти за холст", 1600f, item.centerX, 0f)
        assertEquals("центр Y не должен уйти за холст", 900f, item.centerY, 0f)

        ItemGestures.dragItem(item, Offset(-100_000f, -100_000f), 1f, 1600f, 900f)
        assertEquals(0f, item.centerX, 0f)
        assertEquals(0f, item.centerY, 0f)
    }

    @Test
    fun `dragged frame still moves inside the canvas`() {
        val item = ItemDraft(
            id = "i", zIndex = 0, centerX = 800f, centerY = 450f,
            width = 100f, height = 100f, contents = mutableListOf(),
        )
        ItemGestures.dragItem(item, Offset(50f, -25f), 1f, 1600f, 900f)
        assertEquals(850f, item.centerX, 0f)
        assertEquals(425f, item.centerY, 0f)
    }

    // ---------- ViewModel ----------

    private fun builder(): ComboBuilderViewModel {
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("photos" to "p1")),
            FakeComboRepository(),
            FakePhotoRepository(listOf(photoEntity("p1"))),
            StubBitmapSource(),
        )
        settle()
        assertTrue("билдер не загрузился", vm.state.value.loaded)
        return vm
    }

    @Test
    fun `frame centre stays on canvas after a strong drag`() {
        val vm = builder()
        val itemId = vm.state.value.items.single().id

        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset.Zero, GesturePhase.START))
        repeat(20) { vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset(5000f, 5000f), GesturePhase.CHANGE)) }
        vm.onEvent(BuilderEvent.ItemDrag(itemId, Offset.Zero, GesturePhase.END))

        val b = vm.state.value.items.single().border()
        assertTrue("центр X вне холста: ${b.centerX}", b.centerX in 0f..vm.state.value.canvasW)
        assertTrue("центр Y вне холста: ${b.centerY}", b.centerY in 0f..vm.state.value.canvasH)
    }

    @Test
    fun `ResetViewport restores zoom and pan and leaves no undo step`() {
        val vm = builder()
        vm.onEvent(BuilderEvent.ViewportPan(Offset(120f, 80f)))
        vm.onEvent(BuilderEvent.ViewportZoom(2f, Offset(500f, 300f)))
        assertTrue("предусловие: вид увеличен", vm.state.value.viewport.zoom != 1f)

        vm.onEvent(BuilderEvent.ResetViewport)

        assertEquals(Viewport(), vm.state.value.viewport)
        assertEquals(1f, vm.state.value.viewport.zoom, 0f)
        assertEquals(Offset.Zero, vm.state.value.viewport.pan)
        assertFalse("сдвиг и зум вида в Undo не входят", vm.state.value.canUndo)
    }

    @Test
    fun `viewport zoom keeps the focus point and clamps to the allowed range`() {
        val vm = builder()
        vm.onEvent(BuilderEvent.SetScreenSize(1000f, 600f))
        val s = vm.state.value
        val vsBefore = s.viewport.scale(Size(s.canvasW, s.canvasH), Size(1000f, 600f))
        val focus = Offset(700f, 400f)
        val origin = Offset(
            (1000f - s.canvasW * vsBefore) / 2f + s.viewport.pan.x,
            (600f - s.canvasH * vsBefore) / 2f + s.viewport.pan.y,
        )
        val canvasBefore = canvasPointUnder(focus, origin, vsBefore)

        vm.onEvent(BuilderEvent.ViewportZoom(1.75f, focus))

        val after = vm.state.value
        val vsAfter = after.viewport.scale(Size(after.canvasW, after.canvasH), Size(1000f, 600f))
        val originAfter = Offset(
            (1000f - after.canvasW * vsAfter) / 2f + after.viewport.pan.x,
            (600f - after.canvasH * vsAfter) / 2f + after.viewport.pan.y,
        )
        val canvasAfter = canvasPointUnder(focus, originAfter, vsAfter)

        assertEquals("зум обязан примениться", 1.75f, after.viewport.zoom, 1e-4f)
        assertEquals(canvasBefore.x, canvasAfter.x, 1e-2f)
        assertEquals(canvasBefore.y, canvasAfter.y, 1e-2f)

        // Границы зума (0.5..4) сохраняются, и панорама считается от ПРИМЕНЁННОГО фактора.
        vm.onEvent(BuilderEvent.ViewportZoom(100f, focus))
        assertEquals(4f, vm.state.value.viewport.zoom, 1e-4f)
        vm.onEvent(BuilderEvent.ViewportZoom(0.0001f, focus))
        assertEquals(0.5f, vm.state.value.viewport.zoom, 1e-4f)
    }
}