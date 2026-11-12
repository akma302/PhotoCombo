package com.combo.feature.builder

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.combo.core.render.CanvasTransform
import com.combo.core.render.Edge
import kotlin.math.abs
import kotlin.math.hypot

/** Куда пошёл жест: рамка едет, её фото едет, рамка тянется гранью или едет вид. */
internal enum class GestureKind { NONE, DRAG_ITEM, PAN_CONTENT, RESIZE, PAN_VIEWPORT }

/**
 * Выбор цели жеста в момент касания холста: что именно тронул пользователь. Чистая
 * функция без побочных эффектов.
 *
 * Порядок проверок значим:
 *
 * 1. Грань выбранной рамки в режиме BORDER — изменение размера. Режим проверяется
 *    первым: за грань рамку тянут только в BORDER, а в EDIT тот же палец у границы
 *    перетаскивает рамку, как и в любом другом её месте (см. п. 3).
 * 2. Попадание в СВОЮ выделенную рамку в режиме BORDER — сдвиг фото внутри рамки.
 * 3. Попадание в любую другую рамку (или в свою, но в режиме EDIT) — перетаскивание
 *    рамки.
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
 * Грань рамки под точкой: точка и допуск — в ОДНОМ канвасном пространстве, как и у
 * `hitTest`. Экранный допуск переводится сюда делением на масштаб вида, поэтому
 * попадание по грани не зависит от зума.
 *
 * Точка в углу попадает в горизонтальную грань: приоритет — левая/правая, затем
 * верхняя/нижняя. Чистая функция: проверяется юнит-тестом без Compose.
 */
internal fun nearEdge(p: Offset, border: Rect, tolerance: Float): Edge? {
    if (p.x < border.left - tolerance || p.x > border.right + tolerance ||
        p.y < border.top - tolerance || p.y > border.bottom + tolerance
    ) return null
    return when {
        abs(p.x - border.left) <= tolerance -> Edge.LEFT
        abs(p.x - border.right) <= tolerance -> Edge.RIGHT
        abs(p.y - border.top) <= tolerance -> Edge.TOP
        abs(p.y - border.bottom) <= tolerance -> Edge.BOTTOM
        else -> null
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
 * Press and movement share a snapshot. Classify against the old selection before a
 * mouse press changes it, so dragging another frame in Photo mode still moves the frame.
 */
internal fun Modifier.editorGestures(
    vm: ComboBuilderViewModel,
    handlePx: State<Float>,
    state: State<BuilderUiState>,
    transform: State<CanvasTransform>,
): Modifier {
    fun toCanvas(p: Offset): Offset = transform.value.toCanvas(p)

    fun edgeHitAt(p: Offset, itemId: String?): Edge? {
        val item = state.value.items.firstOrNull { it.id == itemId } ?: return null
        return nearEdge(
            toCanvas(p),
            item.border().rect(),
            handlePx.value / transform.value.scale,
        )
    }

    return this.pointerInput(Unit) {
        var editStarted = false
        var previousUp: PointerInputChange? = null
        var previousItemId: String? = null

        try {
            awaitEachGesture {
                val down = awaitFirstDown()
                val mouse = down.type == PointerType.Mouse
                val bringToFront = !currentEvent.keyboardModifiers.isShiftPressed
                val initial = state.value
                val hitId = vm.hitTest(toCanvas(down.position))
                val edge = edgeHitAt(down.position, initial.selectedItemId)
                val kind = classifyGesture(initial.mode, hitId, initial.selectedItemId, edge)
                val targetId = when (kind) {
                    GestureKind.RESIZE, GestureKind.PAN_CONTENT -> initial.selectedItemId
                    GestureKind.DRAG_ITEM -> hitId
                    else -> null
                }
                down.consume()
                if (mouse && targetId != null) {
                    if (initial.saving) vm.onEvent(BuilderEvent.ItemTapped(targetId))
                    else {
                        editStarted = true
                        vm.onEvent(BuilderEvent.ItemPressed(targetId, bringToFront))
                    }
                }

                var up: PointerInputChange? = null
                var pastSlop = false
                var multiplePointers = false
                var prev = down.position
                var prevSpan = 0f
                var lastCount = 1
                var slopRef = down.position
                var spanRef = 0f

                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) continue
                    if (event.changes.any { it.isConsumed }) break
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) {
                        up = event.changes.firstOrNull {
                            it.changedToUp() && (pastSlop || !it.isOutOfBounds(size, extendedTouchPadding))
                        }
                        up?.consume()
                        break
                    }
                    val centroid = centroidOf(pressed)
                    multiplePointers = multiplePointers || pressed.size > 1
                    val span = if (pressed.size >= 2)
                        hypot(
                            pressed[0].position.x - pressed[1].position.x,
                            pressed[0].position.y - pressed[1].position.y,
                        ) else 0f
                    val countChanged = pressed.size != lastCount
                    lastCount = pressed.size
                    if (countChanged) {
                        if (!pastSlop) {
                            slopRef = centroid
                            spanRef = span
                        } else {
                            event.changes.forEach { it.consume() }
                        }
                        prev = centroid
                        prevSpan = span
                        continue
                    }
                    val moved = abs(centroid.x - slopRef.x) > viewConfiguration.touchSlop ||
                        abs(centroid.y - slopRef.y) > viewConfiguration.touchSlop ||
                        (spanRef > 0f && abs(span - spanRef) > viewConfiguration.touchSlop)
                    if (!pastSlop && moved) {
                        pastSlop = true
                        if (!mouse && targetId != null && !initial.saving) {
                            editStarted = true
                            vm.onEvent(BuilderEvent.ItemPressed(targetId, bringToFront = false))
                        }
                    }
                    if (pastSlop) {
                        val delta = centroid - prev
                        val id = targetId?.takeIf { editStarted }
                        when (kind) {
                            GestureKind.RESIZE -> if (id != null && edge != null) vm.onEvent(
                                BuilderEvent.BorderResize(id, edge, delta, GesturePhase.CHANGE)
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
                        event.changes.forEach { it.consume() }
                    }
                    prev = centroid
                    prevSpan = span
                }

                if (editStarted) {
                    vm.onEvent(BuilderEvent.PointerEditFinished(cancelled = up == null))
                    editStarted = false
                }
                if (pastSlop) vm.refreshBitmaps()
                if (up == null || pastSlop || multiplePointers) {
                    previousUp = null
                    return@awaitEachGesture
                }

                val itemId = if (mouse) targetId else vm.hitTest(toCanvas(up.position))
                if (itemId == null) {
                    vm.onEvent(BuilderEvent.CanvasTapped)
                } else if (!mouse) {
                    vm.onEvent(BuilderEvent.ItemPressed(itemId, bringToFront))
                    vm.onEvent(BuilderEvent.PointerEditFinished(cancelled = false))
                }
                val isDoubleTap = previousUp?.let { first ->
                    val interval = down.uptimeMillis - first.uptimeMillis
                    interval in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                        itemId == previousItemId && (down.position - first.position).getDistance() <= viewConfiguration.touchSlop
                } ?: false
                if (isDoubleTap) vm.onEvent(BuilderEvent.ToggleMode)
                previousUp = up.takeUnless { isDoubleTap }
                previousItemId = itemId
            }
        } finally {
            if (editStarted) vm.onEvent(BuilderEvent.PointerEditFinished(cancelled = true))
        }
    }
}

private fun centroidOf(pointers: List<PointerInputChange>): Offset {
    var x = 0f; var y = 0f
    pointers.forEach { x += it.position.x; y += it.position.y }
    return Offset(x / pointers.size, y / pointers.size)
}
