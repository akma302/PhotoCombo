package com.combo.feature.builder

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.combo.core.render.CanvasTransform
import kotlin.math.pow
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Зум на один щелчок колеса; дробные значения (тачпады) дают плавный зум. */
private const val WHEEL_ZOOM_STEP = 1.1f

/**
 * Пауза после последнего тика колеса, по которой серия считается завершённой. Пока серия
 * открыта, тики копятся в один undo-шаг: снимок берётся на первом тике, checkpoint — на
 * END, поэтому у серии ровно один undo-шаг, а не по шагу на каждый тик.
 */
private const val WHEEL_IDLE_END_MS = 250L

/**
 * Колесо мыши как замена щипка: центр масштаба — точка курсора.
 *
 * Цель выбирается тем же [classifyGesture], что и у касаний: рамка в EDIT (или чужая
 * рамка) масштабируется целиком ([BuilderEvent.ItemPinch]), выбранная рамка в BORDER —
 * только её фото ([BuilderEvent.ContentZoom]), пустое место — вид ([BuilderEvent.ViewportZoom]).
 * Ctrl+колесо всегда зумит вид: так вид можно увеличить, не уводя курсор с рамки.
 *
 * В отличие от касаний, у колеса нет UP: серия тиков завершается по паузе
 * [WHEEL_IDLE_END_MS], поэтому `START` и `END` живут в этом блоке, а не в VM.
 */
internal fun Modifier.editorWheelZoom(
    vm: ComboBuilderViewModel,
    state: State<BuilderUiState>,
    transform: State<CanvasTransform>,
): Modifier = this.pointerInput(Unit) {
    coroutineScope {
        var seriesKind = GestureKind.NONE
        var seriesId: String? = null
        var endJob: Job? = null

        fun endSeries() {
            endJob?.cancel()
            endJob = null
            val id = seriesId
            when (seriesKind) {
                GestureKind.DRAG_ITEM ->
                    if (id != null) vm.onEvent(BuilderEvent.ItemPinch(id, 1f, GesturePhase.END))
                GestureKind.PAN_CONTENT ->
                    if (id != null) vm.onEvent(BuilderEvent.ContentZoom(id, 1f, GesturePhase.END))
                else -> {}
            }
            seriesKind = GestureKind.NONE
            seriesId = null
            if (id != null) vm.refreshBitmaps()
        }

        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val dy = change.scrollDelta.y
                if (dy == 0f) continue
                change.consume()

                // Вверх по колесу (dy < 0) — увеличение, вниз — уменьшение.
                val factor = WHEEL_ZOOM_STEP.pow(-dy)
                val st = state.value
                val position = change.position
                val kind = if (event.keyboardModifiers.isCtrlPressed) {
                    GestureKind.PAN_VIEWPORT
                } else {
                    classifyGesture(
                        st.mode,
                        vm.hitTest(transform.value.toCanvas(position)),
                        st.selectedItemId,
                        null,
                    )
                }

                if (kind == GestureKind.PAN_VIEWPORT) {
                    // Вид не входит в undo, поэтому у него нет ни фаз, ни серии.
                    endSeries()
                    vm.onEvent(BuilderEvent.ViewportZoom(factor, position))
                    continue
                }

                val id = when (kind) {
                    GestureKind.PAN_CONTENT -> st.selectedItemId
                    GestureKind.DRAG_ITEM -> vm.hitTest(transform.value.toCanvas(position))
                    else -> null
                }
                // Смена цели посреди серии (курсор ушёл на другую рамку) завершает
                // предыдущую серию, иначе её undo-шаг остался бы открытым навсегда.
                if (kind != seriesKind || id != seriesId) {
                    endSeries()
                    if (kind == GestureKind.DRAG_ITEM && id != null) {
                        vm.onEvent(BuilderEvent.ItemTapped(id))
                    }
                    seriesKind = kind
                    seriesId = id
                    when (kind) {
                        GestureKind.DRAG_ITEM ->
                            if (id != null) vm.onEvent(BuilderEvent.ItemPinch(id, 1f, GesturePhase.START))
                        GestureKind.PAN_CONTENT ->
                            if (id != null) vm.onEvent(BuilderEvent.ContentZoom(id, 1f, GesturePhase.START))
                        else -> {}
                    }
                }
                when (kind) {
                    GestureKind.DRAG_ITEM ->
                        if (id != null) vm.onEvent(BuilderEvent.ItemPinch(id, factor, GesturePhase.CHANGE))
                    GestureKind.PAN_CONTENT ->
                        if (id != null) vm.onEvent(BuilderEvent.ContentZoom(id, factor, GesturePhase.CHANGE))
                    else -> {}
                }
                endJob = launch {
                    delay(WHEEL_IDLE_END_MS)
                    endSeries()
                }
            }
        }
    }
}
