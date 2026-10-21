package com.combo.feature.builder

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.combo.core.render.CanvasTransform
import com.combo.core.render.Edge
import com.combo.core.render.flingDirection
import com.combo.core.render.safeVelocity
import kotlin.math.abs
import kotlin.math.hypot

/** Куда пошёл жест: рамка едет, её содержимое едет, рамка тянется гранью или едет вид. */
internal enum class GestureKind { NONE, DRAG_ITEM, PAN_CONTENT, RESIZE, PAN_VIEWPORT }

/**
 * Выбор цели жеста в момент касания холста: что именно тронул пользователь. Чистая
 * функция без побочных эффектов; события START для выбранной цели отправляет вызывающий
 * ([startGesture]).
 *
 * Порядок проверок значим:
 *
 * 1. Грань выбранной рамки в режиме BORDER — изменение размера. Режим проверяется
 *    первым: за грань рамку тянут только в BORDER, а в EDIT тот же палец у границы
 *    перетаскивает рамку, как и в любом другом её месте (см. п. 3).
 * 2. Попадание в СВОЮ выделенную рамку в режиме BORDER — сдвиг фото внутри рамки.
 * 3. Попадание в любую другую рамку (или в свою, но в режиме EDIT) — перетаскивание
 *    рамки; перед этим она выделяется ([BuilderEvent.ItemTapped]).
 * 4. Мимо всех рамок — сдвиг и зум ВИДА (не самого холста).
 */
internal fun classifyGesture(
    mode: EditMode,
    hitId: String?, // попали ли в рамку
    selectedId: String?,
    edgeHit: Edge?, // грань под пальцем
): GestureKind = when {
    mode == EditMode.BORDER && selectedId != null && edgeHit != null -> GestureKind.RESIZE
    hitId != null && hitId == selectedId && mode == EditMode.BORDER -> GestureKind.PAN_CONTENT
    hitId != null -> GestureKind.DRAG_ITEM
    else -> GestureKind.PAN_VIEWPORT
}

/**
 * Отправляет событие СТАРТА для выбранной цели. Это отдельная функция, а не часть
 * [classifyGesture], потому что [classifyGesture] должна оставаться чистой: её проверяет
 * юнит-тест без Compose.
 *
 * Для DRAG_ITEM порядок обязателен: `ItemTapped` идёт ПЕРЕД `ItemDrag(START)`, чтобы
 * рамка была выделена к моменту, когда она начнёт двигаться. У сдвига и зума вида событий
 * нет вовсе: это состояние редактора, оно не входит в undo.
 */
private fun startGesture(
    kind: GestureKind,
    id: String?,
    edge: Edge?,
    emit: (BuilderEvent) -> Unit,
) {
    when {
        kind == GestureKind.RESIZE && id != null && edge != null ->
            emit(BuilderEvent.BorderResize(id, edge, Offset.Zero, GesturePhase.START))
        kind == GestureKind.PAN_CONTENT && id != null ->
            emit(BuilderEvent.ContentPan(id, Offset.Zero, GesturePhase.START))
        kind == GestureKind.DRAG_ITEM && id != null -> {
            emit(BuilderEvent.ItemTapped(id))
            emit(BuilderEvent.ItemDrag(id, Offset.Zero, GesturePhase.START))
        }
        else -> {}
    }
}

/**
 * Распознаватель жестов холста редактора — единый блок `pointerInput(Unit)`.
 *
 * КЛЮЧ `Unit` НЕ МЕНЯТЬ: блок запускается один раз и живёт, пока на экране холст, поэтому
 * всё, что зависит от композиции ([state], [transform], [handlePx]), передаётся
 * `State`-обёртками и читается в момент события. Если вместо этого сделать значения
 * ключами, смена выделения или режима посреди жеста перезапустит блок: жест оборвётся
 * без END (потеряются undo-шаг и `isDirty`), а захваченные значения устареют. Это
 * проверяет `ComboBuilderGesturesTest`.
 *
 * Тонкости, которые обязаны остаться как есть:
 *
 * - опорная точка slop (и опорный размах щипка) переопределяется при смене числа пальцев
 *   ДО slop: центроид при появлении второго пальца прыгает, и без этого «просто коснулся двумя
 *   пальцами» начинало бы жест (пустой undo-шаг и `isDirty`);
 * - опорный размах щипка сравнивается с ПРЕДЫДУЩИМ событием осмысленно только потому, что он
 *   не обнуляется на каждом шаге: при плавном симметричном разведении шагами меньше
 *   touchSlop размах иначе никогда не превысил бы slop относительно прошлого события, а
 *   центроид при этом стоял бы на месте — жест не начался бы никогда;
 * - `consume()` только ПОСЛЕ slop: иначе лёгкий сдвиг пальца «съедает» событие, и
 *   `detectTapGestures` не распознаёт тап (он отменяет тап по `consumed`);
 * - на флинге — CANCEL, а не END (см. ниже);
 * - у плеера свой жестовый цикл и свой [VelocityTracker]: общие у двух экранов только
 *   [flingDirection] и [safeVelocity].
 */
internal fun Modifier.editorGestures(
    vm: ComboBuilderViewModel,
    handlePx: Float,
    state: State<BuilderUiState>,
    transform: State<CanvasTransform>,
): Modifier {
    fun toCanvas(p: Offset): Offset = transform.value.toCanvas(p)

    fun nearEdge(p: Offset, itemId: String?): Edge? {
        val item = state.value.items.firstOrNull { it.id == itemId } ?: return null
        val r = item.border().rect().translate(transform.value.origin)
        if (p.x < r.left - handlePx || p.x > r.right + handlePx ||
            p.y < r.top - handlePx || p.y > r.bottom + handlePx
        ) return null
        return when {
            abs(p.x - r.left) <= handlePx -> Edge.LEFT
            abs(p.x - r.right) <= handlePx -> Edge.RIGHT
            abs(p.y - r.top) <= handlePx -> Edge.TOP
            abs(p.y - r.bottom) <= handlePx -> Edge.BOTTOM
            else -> null
        }
    }

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            var kind: GestureKind = GestureKind.NONE
            // Рамка, зафиксированная на START: смена выделения посреди жеста
            // больше не должна перенаправлять CHANGE/END на другую рамку.
            var targetId: String? = null
            var resizeEdge: Edge? = null
            var pastSlop = false
            var prev = down.position
            var prevSpan = 0f
            var lastCount = 1
            // Опорная точка для slop. При появлении второго пальца центроид
            // прыгает, поэтому ДО slop она переопределяется — иначе «просто
            // коснулся двумя пальцами» начинало бы жест (пустой undo-шаг,
            // isDirty). После slop опорная точка уже не нужна.
            var slopRef = down.position
            // Опорный размах для щипка. Сравнивать span с ПРЕДЫДУЩИМ событием
            // бессмысленно: при плавном симметричном разведении шагами меньше
            // touchSlop размах никогда не превысит slop относительно прошлого
            // события, а центроид при этом стоит на месте — жест не начинался.
            var spanRef = 0f

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val main = pressed.first()
                val centroid = centroidOf(pressed)
                val span = if (pressed.size >= 2)
                    hypot(
                        pressed[0].position.x - pressed[1].position.x,
                        pressed[0].position.y - pressed[1].position.y,
                    ) else 0f

                val countChanged = pressed.size != lastCount
                if (countChanged && !pastSlop) {
                    slopRef = centroid
                    spanRef = span
                    prev = centroid
                    prevSpan = span
                    lastCount = pressed.size
                    continue
                }
                val moved = abs(centroid.x - slopRef.x) > viewConfiguration.touchSlop ||
                    abs(centroid.y - slopRef.y) > viewConfiguration.touchSlop ||
                    (pressed.size >= 2 && spanRef > 0f &&
                        abs(span - spanRef) > viewConfiguration.touchSlop)

                if (!pastSlop && moved) {
                    pastSlop = true
                    val st = state.value
                    val selectedId = st.selectedItemId
                    val hitId = vm.hitTest(toCanvas(down.position))
                    val edgeHit = nearEdge(down.position, selectedId)
                    kind = classifyGesture(st.mode, hitId, selectedId, edgeHit)
                    targetId = when (kind) {
                        GestureKind.RESIZE, GestureKind.PAN_CONTENT -> selectedId
                        GestureKind.DRAG_ITEM -> hitId
                        else -> null
                    }
                    resizeEdge = if (kind == GestureKind.RESIZE) edgeHit else null
                    startGesture(kind, targetId, resizeEdge, vm::onEvent)
                }

                lastCount = pressed.size
                if (pastSlop) {
                    tracker.addPosition(main.uptimeMillis, main.position)
                    val id = targetId
                    if (countChanged) {
                        prev = centroid
                        prevSpan = span
                    } else {
                        val delta = centroid - prev
                        when (kind) {
                            GestureKind.RESIZE -> if (id != null && resizeEdge != null) vm.onEvent(
                                BuilderEvent.BorderResize(id, resizeEdge, delta, GesturePhase.CHANGE)
                            )
                            GestureKind.DRAG_ITEM -> if (id != null) {
                                val f = if (prevSpan > 0f && span > 0f) span / prevSpan else 1f
                                vm.onEvent(BuilderEvent.ItemDrag(id, delta, GesturePhase.CHANGE))
                                if (span > 0f && prevSpan > 0f)
                                    vm.onEvent(BuilderEvent.ItemPinch(id, f, GesturePhase.CHANGE))
                            }
                            GestureKind.PAN_CONTENT -> if (id != null) {
                                val f = if (prevSpan > 0f && span > 0f) span / prevSpan else 1f
                                vm.onEvent(BuilderEvent.ContentPan(id, delta, GesturePhase.CHANGE))
                                if (span > 0f && prevSpan > 0f)
                                    vm.onEvent(BuilderEvent.ContentZoom(id, f, GesturePhase.CHANGE))
                            }
                            GestureKind.PAN_VIEWPORT -> {
                                // Щипок по пустому месту зумит ВИД, а не рамку.
                                // Фокус — центроид пальцев, иначе холст
                                // «уезжает» из-под них (см. viewportZoomPan).
                                vm.onEvent(BuilderEvent.ViewportPan(delta))
                                if (span > 0f && prevSpan > 0f) {
                                    vm.onEvent(BuilderEvent.ViewportZoom(span / prevSpan, centroid))
                                }
                            }
                            GestureKind.NONE -> {}
                        }
                        prev = centroid
                        prevSpan = span
                    }
                    // consume() только ПОСЛЕ slop, иначе лёгкий сдвиг съедает тап:
                    // detectTapGestures отменяет тап, если событие уже consumed.
                    event.changes.forEach { it.consume() }
                } else {
                    prev = centroid
                    prevSpan = span
                }
            }

            if (pastSlop) {
                val id = targetId
                val v = tracker.safeVelocity()
                val dir = if (kind == GestureKind.PAN_CONTENT &&
                    state.value.mode == EditMode.BORDER && id != null
                ) flingDirection(v) else null
                if (dir != null && id != null) {
                    // CANCEL, а не END: жест шёл к СМЕНЕ фото, поэтому сдвиг
                    // от пальца не должен закрепляться на предыдущем фото —
                    // END коммитил бы его и он остался бы в undo-шаге.
                    vm.onEvent(BuilderEvent.ContentPan(id, Offset.Zero, GesturePhase.CANCEL))
                    vm.onEvent(BuilderEvent.ContentFling(id, dir))
                } else {
                    when (kind) {
                        GestureKind.RESIZE -> if (id != null && resizeEdge != null) vm.onEvent(
                            BuilderEvent.BorderResize(id, resizeEdge, Offset.Zero, GesturePhase.END)
                        )
                        GestureKind.DRAG_ITEM -> if (id != null) vm.onEvent(
                            BuilderEvent.ItemDrag(id, Offset.Zero, GesturePhase.END)
                        )
                        GestureKind.PAN_CONTENT -> if (id != null) vm.onEvent(
                            BuilderEvent.ContentPan(id, Offset.Zero, GesturePhase.END)
                        )
                        else -> {}
                    }
                }
                vm.refreshBitmaps()
            }
        }
    }
}

private fun centroidOf(pointers: List<PointerInputChange>): Offset {
    var x = 0f; var y = 0f
    pointers.forEach { x += it.position.x; y += it.position.y }
    return Offset(x / pointers.size, y / pointers.size)
}