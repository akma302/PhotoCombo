package com.combo.feature.builder

import com.combo.core.render.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Выбор цели жеста ([classifyGesture]) — единственное, что в распознавателе редактора можно
 * проверить без Compose.
 *
 * Проверяется ровно то, что не очевидно из чтения кода: ПРИОРИТЕТ ветвей. Каждая пара
 * «сработала бы другая ветка, если бы порядок был другим» проверяется своим случаем, потому
 * что перестановка `when` здесь не падает на типах — она молча меняет смысл жеста:
 * рамка вместо ресайза, содержимое вместо перетаскивания, вид вместо рамки.
 */
class GestureClassifierTest {

    private fun classify(
        mode: EditMode,
        hitId: String?,
        selectedId: String? = null,
        edge: Edge? = null,
    ) = classifyGesture(mode = mode, hitId = hitId, selectedId = selectedId, edgeHit = edge)

    // ---------- ресайз ----------

    /**
     * Грань выбранной рамки в режиме BORDER — ресайз. Все четыре грани равнозначны, но
     * перечислены явно: `Edge` может расшириться, и забытая грань уехала бы в другую ветку.
     */
    @Test
    fun `edge of the selected frame in border mode is a resize`() {
        Edge.entries.forEach { edge ->
            assertEquals(
                "грань $edge должна давать ресайз",
                GestureKind.RESIZE,
                classify(EditMode.BORDER, hitId = "a", selectedId = "a", edge = edge),
            )
        }
    }

    /**
     * Грань важнее попадания внутрь рамки: палец у самого края рамки попадает и внутрь
     * (`hitId` найден), и в зону маркера — ресайз должен выиграть, иначе ресайз был бы
     * недостижим вообще.
     */
    @Test
    fun `resize wins over dragging the very same frame`() {
        assertEquals(
            GestureKind.RESIZE,
            classify(EditMode.BORDER, hitId = "a", selectedId = "a", edge = Edge.LEFT),
        )
    }

    /**
     * Грань выбранной рамки важнее и «промаха» по рамке: зона маркера шире самой рамки
     * (handlePx в обе стороны), поэтому в пределах допуска попадания под палец
     * `hitTest` может вернуть null. Ресайз всё равно должен состояться.
     */
    @Test
    fun `resize wins even when the point missed the frame rect`() {
        assertEquals(
            GestureKind.RESIZE,
            classify(EditMode.BORDER, hitId = null, selectedId = "a", edge = Edge.RIGHT),
        )
    }

    /**
     * Грань принадлежит ВЫБРАННОЙ рамке: если палец у её грани лежит поверх чужой рамки,
     * ресайз всё равно выигрывает — иначе выбранную рамку нельзя было бы растянуть под
     * соседнюю.
     */
    @Test
    fun `resize wins over dragging a different frame under the finger`() {
        assertEquals(
            GestureKind.RESIZE,
            classify(EditMode.BORDER, hitId = "b", selectedId = "a", edge = Edge.LEFT),
        )
    }

    /** В режиме EDIT грани не работают: рамку двигают за любую её точку, а не за край. */
    @Test
    fun `edge in edit mode is not a resize`() {
        assertEquals(
            GestureKind.DRAG_ITEM,
            classify(EditMode.EDIT, hitId = "a", selectedId = "a", edge = Edge.LEFT),
        )
    }

    // ---------- панорама содержимого ----------

    /** Своя выделенная рамка в BORDER без маркера под пальцем — панорама содержимого. */
    @Test
    fun `inside own selected frame in border mode pans the content`() {
        assertEquals(
            GestureKind.PAN_CONTENT,
            classify(EditMode.BORDER, hitId = "a", selectedId = "a", edge = null),
        )
    }

    /** Та же точка без выделения рамки — уже перетаскивание: выделять и тащить. */
    @Test
    fun `inside a frame without selection drags it`() {
        assertEquals(
            GestureKind.DRAG_ITEM,
            classify(EditMode.BORDER, hitId = "a", selectedId = null, edge = null),
        )
    }

    /** Выделена ДРУГАЯ рамка: чужая рамка едет, а не содержимое выбранной. */
    @Test
    fun `inside a non selected frame drags that frame`() {
        assertEquals(
            GestureKind.DRAG_ITEM,
            classify(EditMode.BORDER, hitId = "b", selectedId = "a", edge = null),
        )
    }

    // ---------- вид ----------

    /** Мимо всех рамок — сдвиг/зум ВИДА. Выделенная рамка на это не влияет. */
    @Test
    fun `miss on empty space pans the viewport`() {
        assertEquals(
            GestureKind.PAN_VIEWPORT,
            classify(EditMode.BORDER, hitId = null, selectedId = "a", edge = null),
        )
        assertEquals(
            GestureKind.PAN_VIEWPORT,
            classify(EditMode.EDIT, hitId = null, selectedId = "a", edge = null),
        )
    }

    /** Без выделения грани не бывает, поэтому мимо рамки в BORDER — тоже вид. */
    @Test
    fun `edge without a selected frame cannot resize`() {
        assertEquals(
            GestureKind.PAN_VIEWPORT,
            classify(EditMode.BORDER, hitId = null, selectedId = null, edge = Edge.TOP),
        )
    }

    // ---------- полнота ----------

    /**
     * NONE не выдаётся никогда: это значение «жест ещё не начался», и до slop цикл жестов
     * вообще не спрашивает классификатор. Если бы он появился среди результатов, CHANGE/END
     * молча ничего бы не отправили, а битмапы перезагрузились бы — то есть жест прошёл бы
     * «вхолостую».
     */
    @Test
    fun `classifier never returns none`() {
        val ids = listOf(null, "a", "b")
        for (mode in EditMode.entries) {
            for (hit in ids) {
                for (selected in ids) {
                    for (edge in listOf<Edge?>(null) + Edge.entries) {
                        assertFalse(
                            "mode=$mode hit=$hit selected=$selected edge=$edge",
                            classify(mode, hit, selected, edge) == GestureKind.NONE,
                        )
                    }
                }
            }
        }
    }
}
