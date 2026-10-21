package com.combo.feature.builder

import androidx.compose.ui.geometry.Size
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.render.ItemDraft
import com.combo.core.render.TransformMath
import com.combo.data.srcSize
import com.combo.feature.builder.draft.DraftEditor
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.photoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Чистые операции над драфтами редактора ([DraftEditor]).
 *
 * Общая проверка для всех мутирующих операций — ГРАНИЦА ИЗМЕНЕНИЯ: лямбда
 * `beforeApply` (место для `checkpointNow()` в ViewModel) обязана вызываться ровно один
 * раз и только когда что-то правда меняется. Если забыть этот инвариант, ViewModel
 * начнёт оставлять пустые undo-шаги — лишний Undo, который ничего не отменяет, — и это
 * не поймает ни один тест уровня ViewModel, потому что там результат откатывается в
 * соседнем жесте.
 *
 * Геометрия рамок в тестах подобрана так, чтобы вписанный и заполняющий масштабы
 * различались: рамка 800x450, фото [photoEntity] 1200x800 → fit = 0.5625, fill = 0.6667.
 * Иначе `fit` и `fill` были бы неразличимы.
 */
class DraftEditorTest {

    /** Фото 1200x800 из `ui/TestDoubles`. */
    private val photo = photoEntity("p1")

    private val srcSizeOf: (String) -> Size? = { id -> if (id.startsWith("p")) photo.srcSize() else null }

    /** Счётчик id новых рамок: конкретные значения тесту не важны, важна уникальность. */
    private var nextId = 0
    private fun newId(): String = "gen-${nextId++}"

    /** Рамка 800x450 в центре холста 1600x900 с одним фото [photoId] (масштаб 1, без сдвига). */
    private fun item(
        id: String,
        photoId: String = "p1",
        framingMode: FramingMode = FramingMode.FREE,
        zIndex: Int = 0,
    ) = ItemDraft(
        id = id,
        zIndex = zIndex,
        centerX = 800f,
        centerY = 450f,
        width = 800f,
        height = 450f,
        framingMode = framingMode,
        photoId = photoId,
        contentScale = 1f,
        panX = 0f,
        panY = 0f,
    )

    /** Счётчик «поставили ли checkpoint», чтобы проверять границу изменения. */
    private var checkpoints = 0
    private fun markCheckpoint() {
        checkpoints++
    }

    private val fitScale = TransformMath.fitScale(item("b").border(), Size(1200f, 800f))
    private val fillScale = TransformMath.fillScale(item("b").border(), Size(1200f, 800f))

    // ---------- addItems ----------

    @Test
    fun `each photo gets its own frame on top and is filled to it`() {
        val items = listOf(item("a"), item("b", zIndex = 1))

        val added = DraftEditor.addItems(
            items, listOf(photoEntity("p2"), photoEntity("p3")), 1600f, 900f, ::markCheckpoint, ::newId,
        )!!

        assertEquals("прежний список не меняется", listOf("a", "b"), items.map { it.id })
        assertEquals(4, added.items.size)
        assertEquals(listOf("a", "b"), added.items.take(2).map { it.id })
        assertEquals("N фото дают N рамок", 2, added.addedIds.size)
        val fresh = added.items.drop(2)
        assertEquals(added.addedIds, fresh.map { it.id })
        assertEquals(listOf("p2", "p3"), fresh.map { it.photoId })
        assertEquals("новые рамки — верхние слои, подряд", listOf(2, 3), fresh.map { it.zIndex })
        for (d in fresh) {
            assertEquals(FramingMode.FREE, d.framingMode)
            assertEquals("рамка — половина холста", 800f, d.width, 0f)
            assertEquals(450f, d.height, 0f)
            assertEquals("фото заполняет рамку", fillScale, d.contentScale, 1e-6f)
            assertEquals(0f, d.panX, 0f)
            assertEquals(0f, d.panY, 0f)
        }
        assertEquals(1, checkpoints)
    }

    /** Первая рамка по центру, следующие каскадом: рамки одного запроса не слипаются. */
    @Test
    fun `frames of one request are cascaded`() {
        val added = DraftEditor.addItems(
            emptyList(), List(3) { photoEntity("p$it") }, 1600f, 900f, ::markCheckpoint, ::newId,
        )!!

        val step = 0.04f * 900f
        assertEquals(listOf(800f, 800f + step, 800f + 2 * step), added.items.map { it.centerX })
        assertEquals(listOf(450f, 450f + step, 450f + 2 * step), added.items.map { it.centerY })
    }

    @Test
    fun `cascade keeps the frame centre on the canvas`() {
        val added = DraftEditor.addItems(
            emptyList(), List(Const.MAX_ITEMS) { photoEntity("p$it") }, 100f, 100f, ::markCheckpoint, ::newId,
        )!!

        for (d in added.items) {
            assertTrue(d.centerX in 0f..100f)
            assertTrue(d.centerY in 0f..100f)
        }
    }

    @Test
    fun `an empty combo takes photos up to the limit`() {
        val photos = List(Const.MAX_ITEMS) { photoEntity("p$it") }

        val added = DraftEditor.addItems(emptyList(), photos, 1600f, 900f, ::markCheckpoint, ::newId)!!

        assertEquals(Const.MAX_ITEMS, added.items.size)
        assertEquals(photos.map { it.id }, added.items.map { it.photoId })
    }

    /** Один и тот же id в одном запросе даёт одну рамку. */
    @Test
    fun `duplicate ids inside one request give one frame`() {
        val added = DraftEditor.addItems(
            emptyList(), listOf(photoEntity("p2"), photoEntity("p2"), photoEntity("p3")),
            1600f, 900f, ::markCheckpoint, ::newId,
        )!!

        assertEquals(listOf("p2", "p3"), added.items.map { it.photoId })
        assertEquals(1, checkpoints)
    }

    /** То же фото в другой рамке допустимо: рамка — не уникальный слот фото. */
    @Test
    fun `a photo already used by another frame can be added again`() {
        val items = listOf(item("a", photoId = "p1"))

        val added = DraftEditor.addItems(items, listOf(photoEntity("p1")), 1600f, 900f, ::markCheckpoint, ::newId)!!

        assertEquals(listOf("p1", "p1"), added.items.map { it.photoId })
    }

    @Test
    fun `the request is cut to the remaining room`() {
        val items = List(Const.MAX_ITEMS - 2) { item("i$it", zIndex = it) }

        val added = DraftEditor.addItems(
            items, List(5) { photoEntity("n$it") }, 1600f, 900f, ::markCheckpoint, ::newId,
        )!!

        assertEquals(Const.MAX_ITEMS, added.items.size)
        assertEquals("лишние фото отбрасываются с конца", listOf("n0", "n1"), added.items.takeLast(2).map { it.photoId })
    }

    /** Лимит проверяется ДО checkpoint: на исчерпанном лимите undo-шага быть не должно. */
    @Test
    fun `the item limit blocks adding and leaves no checkpoint`() {
        val items = List(Const.MAX_ITEMS) { item("i$it", zIndex = it) }

        assertNull(DraftEditor.addItems(items, listOf(photoEntity("p9")), 1600f, 900f, ::markCheckpoint, ::newId))
        assertEquals(0, checkpoints)
    }

    @Test
    fun `an empty request changes nothing`() {
        assertNull(DraftEditor.addItems(listOf(item("a")), emptyList(), 1600f, 900f, ::markCheckpoint, ::newId))
        assertEquals(0, checkpoints)
    }

    /** `beforeApply` — точка ДО первой мутации, а не после неё. */
    @Test
    fun `checkpoint is requested before the new frames are created`() {
        var created = -1
        DraftEditor.addItems(
            emptyList(), listOf(photoEntity("p1")), 1600f, 900f,
            beforeApply = { created = nextId },
            newId = ::newId,
        )

        assertEquals("в момент checkpoint ни одна рамка ещё не создана", 0, created)
        assertEquals(1, nextId)
    }

    // ---------- replacePhoto ----------

    @Test
    fun `replacing the photo keeps the frame and fills it with the new photo`() {
        val target = item("i", framingMode = FramingMode.FREE).apply {
            rotation = 15f
            contentScale = 2.5f
            panX = 30f
            panY = -10f
        }

        val changed = DraftEditor.replacePhoto(target, photoEntity("p2"), Size(1200f, 800f), ::markCheckpoint)

        assertTrue(changed)
        assertEquals("p2", target.photoId)
        assertEquals("id рамки не меняется", "i", target.id)
        assertEquals(800f, target.centerX, 0f)
        assertEquals(450f, target.centerY, 0f)
        assertEquals(800f, target.width, 0f)
        assertEquals(450f, target.height, 0f)
        assertEquals(15f, target.rotation, 0f)
        assertEquals("новое фото заполняет рамку", fillScale, target.contentScale, 1e-6f)
        assertEquals(0f, target.panX, 0f)
        assertEquals(0f, target.panY, 0f)
        assertEquals(1, checkpoints)
    }

    /** Размер берётся у НОВОГО фото: у квадрата и у 3:2 заполнение различается. */
    @Test
    fun `the new photo size decides the fill scale`() {
        val target = item("i")
        val square = TransformMath.fillScale(target.border(), Size(900f, 900f))

        DraftEditor.replacePhoto(target, photoEntity("p2"), Size(900f, 900f))

        assertEquals(square, target.contentScale, 1e-6f)
        assertNotEquals(fillScale, target.contentScale)
    }

    @Test
    fun `the framing mode survives a replacement`() {
        val target = item("i", framingMode = FramingMode.FIT)

        DraftEditor.replacePhoto(target, photoEntity("p2"), Size(1200f, 800f))

        assertEquals(FramingMode.FIT, target.framingMode)
        assertEquals("p2", target.photoId)
    }

    @Test
    fun `replacing with the same photo changes nothing`() {
        val target = item("i", photoId = "p1").apply { contentScale = 2f; panX = 5f }

        assertFalse(DraftEditor.replacePhoto(target, photoEntity("p1"), Size(1200f, 800f), ::markCheckpoint))

        assertEquals(2f, target.contentScale, 0f)
        assertEquals(5f, target.panX, 0f)
        assertEquals(0, checkpoints)
    }

    @Test
    fun `checkpoint is requested before the photo is replaced`() {
        val target = item("i", photoId = "p1")
        var photoAtCheckpoint = ""

        DraftEditor.replacePhoto(target, photoEntity("p2"), Size(1200f, 800f)) { photoAtCheckpoint = target.photoId }

        assertEquals("в момент checkpoint рамка ещё со старым фото", "p1", photoAtCheckpoint)
        assertEquals("p2", target.photoId)
    }

    // ---------- reorder ----------

    @Test
    fun `reordering forward swaps the item and renumbers zIndex`() {
        val items = listOf(item("a", zIndex = 0), item("b", zIndex = 1), item("c", zIndex = 2))

        val next = DraftEditor.reorder(items, 0, forward = true, beforeApply = ::markCheckpoint)

        assertEquals(listOf("b", "a", "c"), next!!.map { it.id })
        assertEquals(listOf(0, 1, 2), next.map { it.zIndex })
        assertEquals(1, checkpoints)
    }

    @Test
    fun `reordering backward swaps the item`() {
        val items = listOf(item("a"), item("b"), item("c"))

        val next = DraftEditor.reorder(items, 2, forward = false, beforeApply = ::markCheckpoint)

        assertEquals(listOf("a", "c", "b"), next!!.map { it.id })
    }

    /** У первого назад и у последнего вперёд соседа нет — пустой undo-шаг недопустим. */
    @Test
    fun `there is nothing to reorder at the edges`() {
        val items = listOf(item("a"), item("b"))

        assertNull(DraftEditor.reorder(items, 0, forward = false, beforeApply = ::markCheckpoint))
        assertNull(DraftEditor.reorder(items, 1, forward = true, beforeApply = ::markCheckpoint))
        assertNull("индекс вне списка отвергается", DraftEditor.reorder(items, 5, true, ::markCheckpoint))
        assertNull("пустой список отвергается", DraftEditor.reorder(emptyList(), 0, true, ::markCheckpoint))
        assertEquals(0, checkpoints)
    }

    /** Порядок исходного списка не меняется: перестановку публикует вызывающий. */
    @Test
    fun `the source list keeps its order`() {
        val items = listOf(item("a"), item("b"))

        DraftEditor.reorder(items, 0, forward = true, beforeApply = ::markCheckpoint)

        assertEquals(listOf("a", "b"), items.map { it.id })
    }

    // ---------- deleteItem ----------

    @Test
    fun `deleting an item renumbers zIndex`() {
        val items = listOf(item("a", zIndex = 0), item("b", zIndex = 1), item("c", zIndex = 2))

        val left = DraftEditor.deleteItem(items, "b", ::markCheckpoint)

        assertEquals(listOf("a", "c"), left!!.map { it.id })
        assertEquals(listOf(0, 1), left.map { it.zIndex })
        assertEquals(1, checkpoints)
    }

    @Test
    fun `the only item cannot be deleted`() {
        val items = listOf(item("a"))

        assertNull(DraftEditor.deleteItem(items, "a", ::markCheckpoint))
        assertEquals(1, items.size)
        assertEquals(0, checkpoints)
    }

    /** Несуществующий id отвергается ДО checkpoint — иначе остался бы пустой шаг. */
    @Test
    fun `an unknown id is rejected before the checkpoint`() {
        val items = listOf(item("a"), item("b"))

        assertNull(DraftEditor.deleteItem(items, "zzz", ::markCheckpoint))
        assertEquals(0, checkpoints)
    }

    // ---------- applyPreset ----------

    @Test
    fun `the FILL preset fills the frame and zeroes the pan`() {
        val target = item("i").apply { panX = 20f; panY = 9f }

        assertTrue(DraftEditor.applyPreset(target, FitPreset.FILL, srcSizeOf, { photo }, ::markCheckpoint))

        assertEquals(fillScale, target.contentScale, 1e-6f)
        assertEquals(0f, target.panX, 0f)
        assertEquals(0f, target.panY, 0f)
        assertEquals(1, checkpoints)
    }

    @Test
    fun `the FIT preset fits the whole photo`() {
        val target = item("i")

        assertTrue(DraftEditor.applyPreset(target, FitPreset.FIT, srcSizeOf, { photo }, ::markCheckpoint))

        assertEquals(fitScale, target.contentScale, 1e-6f)
        assertNotEquals("fit и fill обязаны различаться на этой геометрии", fillScale, target.contentScale)
    }

    /**
     * Пресет — явный выбор пользователя по конкретному фото, и он важнее блокировки FIT:
     * режим становится FREE.
     */
    @Test
    fun `a preset in FIT leaves FREE`() {
        val target = item("i", framingMode = FramingMode.FIT)

        assertTrue(DraftEditor.applyPreset(target, FitPreset.FILL, srcSizeOf, { photo }, ::markCheckpoint))

        assertEquals(FramingMode.FREE, target.framingMode)
        assertEquals(fillScale, target.contentScale, 1e-6f)
    }

    /** Без размера фото пресет считать нечем — тихо ничего не меняем, а не ставим пустой шаг. */
    @Test
    fun `a preset for a photo of unknown size changes nothing`() {
        val target = item("i")

        assertFalse(DraftEditor.applyPreset(target, FitPreset.FILL, { null }, { photo }, ::markCheckpoint))
        assertEquals(1f, target.contentScale, 0f)
        assertEquals(FramingMode.FREE, target.framingMode)
        assertEquals(0, checkpoints)
    }

    // ---------- applyFramingMode ----------

    /**
     * Выход из FIT обязан запечь ВИДИМЫЕ значения: без этого фото скакнуло бы к давним
     * сохранённым, которые всё это время не показывались.
     */
    @Test
    fun `leaving FIT bakes the fitted values`() {
        val target = item("i", framingMode = FramingMode.FIT)
        target.contentScale = 99f
        target.panX = 33f

        assertTrue(DraftEditor.applyFramingMode(target, FramingMode.FREE) { photo })

        assertEquals(FramingMode.FREE, target.framingMode)
        assertEquals(fitScale, target.contentScale, 1e-6f)
        assertEquals("в FIT pan всегда нулевой", 0f, target.panX, 0f)
        assertEquals(0f, target.panY, 0f)
    }

    /** На входе в FIT сохранённые значения не трогаем: кнопка AutoFit вернёт пользователю именно их. */
    @Test
    fun `entering FIT keeps the stored values`() {
        val target = item("i")
        target.contentScale = 2.25f
        target.panX = -14f

        assertTrue(DraftEditor.applyFramingMode(target, FramingMode.FIT) { photo })

        assertEquals(FramingMode.FIT, target.framingMode)
        assertEquals(2.25f, target.contentScale, 0f)
        assertEquals(-14f, target.panX, 0f)
    }

    @Test
    fun `switching to the same mode changes nothing`() {
        val target = item("i", framingMode = FramingMode.FIT)
        target.contentScale = 7f
        target.panX = -5f

        assertFalse("FIT → FIT ничего не меняет", DraftEditor.applyFramingMode(target, FramingMode.FIT) { photo })
        assertEquals("FIT → FIT не должен запекать", 7f, target.contentScale, 0f)
        assertEquals(-5f, target.panX, 0f)

        val free = item("j", framingMode = FramingMode.FREE)
        free.contentScale = 3f
        assertFalse("FREE → FREE ничего не меняет", DraftEditor.applyFramingMode(free, FramingMode.FREE) { photo })
        assertEquals(3f, free.contentScale, 0f)
    }

    /** Фото без метаданных при запекании пропускается, а не ломает проход. */
    @Test
    fun `a photo without metadata is skipped while baking`() {
        val target = item("i", framingMode = FramingMode.FIT)
        target.contentScale = 7f

        assertTrue(DraftEditor.applyFramingMode(target, FramingMode.FREE) { null })

        assertEquals(FramingMode.FREE, target.framingMode)
        assertEquals("без метаданных остаётся то, что было", 7f, target.contentScale, 0f)
    }

    // ---------- newComboDrafts ----------

    /**
     * Старт «с нуля» не создаёт пустых рамок: пустых рамок в модели нет, а «+Frame»
     * доступна и без выделения.
     */
    @Test
    fun `no photos means no frames and no selection`() {
        val new = DraftEditor.newComboDrafts(emptyList(), 1600f, 900f, ::newId)

        assertTrue(new.items.isEmpty())
        assertNull(new.selectedItemId)
        assertEquals("id тратить не на что", 0, nextId)
    }

    @Test
    fun `photos are laid out in cells and filled`() {
        val new = DraftEditor.newComboDrafts(List(3) { photoEntity("p$it") }, 1600f, 900f, ::newId)

        assertEquals(3, new.items.size)
        assertEquals(800f, new.items[1].centerX, 1e-3f)
        assertEquals(listOf("p0", "p1", "p2"), new.items.map { it.photoId })
        assertEquals(
            "ячейка заполняется по её собственным размерам",
            TransformMath.fillScale(new.items[0].border(), Size(1200f, 800f)),
            new.items[0].contentScale,
            1e-4f,
        )
        assertEquals(listOf(0, 1, 2), new.items.map { it.zIndex })
        assertEquals(new.items.first().id, new.selectedItemId)
    }

    /** Комбо длиннее [Const.MAX_ITEMS] рамок всё равно не создать — лимит живёт в редакторе. */
    @Test
    fun `more photos than the item limit are cut off`() {
        val new = DraftEditor.newComboDrafts(List(Const.MAX_ITEMS + 2) { photoEntity("p$it") }, 1600f, 900f, ::newId)

        assertEquals(Const.MAX_ITEMS, new.items.size)
        assertEquals(Const.MAX_ITEMS, new.items.map { it.photoId }.distinct().size)
        assertEquals("zIndex перенумеровывается подряд", (0 until Const.MAX_ITEMS).toList(), new.items.map { it.zIndex })
    }

    /** Одно фото — одна рамка во весь холст, а не крошечная ячейка. */
    @Test
    fun `a single photo takes the whole canvas`() {
        val new = DraftEditor.newComboDrafts(listOf(photo), 1600f, 900f, ::newId)

        val only = new.items.single()
        assertEquals(1600f, only.width, 0f)
        assertEquals(900f, only.height, 0f)
        assertEquals("p1", only.photoId)
    }
}
