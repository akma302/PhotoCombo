package com.combo.feature.builder

import androidx.compose.ui.geometry.Size
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.render.ContentDraft
import com.combo.core.render.ItemDraft
import com.combo.core.render.TransformMath
import com.combo.data.PhotoEntity
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

    private fun metas(vararg ids: String): Map<String, PhotoEntity> =
        ids.associateWith { photoEntity(it) }

    private val srcSizeOf: (String) -> Size? = { id -> if (id.startsWith("p")) photo.srcSize() else null }

    /** Рамка 800x450 в центре холста 1600x900. */
    private fun item(
        id: String,
        contents: Int = 0,
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
        contents = MutableList(contents) { i ->
            ContentDraft("$id-ct$i", "p${i + 1}", i, 1f, 0f, 0f)
        },
    )

    /** Счётчик «поставили ли checkpoint», чтобы проверять границу изменения. */
    private var checkpoints = 0
    private fun markCheckpoint() {
        checkpoints++
    }

    private val fitScale = TransformMath.fitScale(
        ItemDraft("b", 0, 800f, 450f, 800f, 450f, contents = mutableListOf()).border(),
        Size(1200f, 800f),
    )
    private val fillScale = TransformMath.fillScale(
        ItemDraft("b", 0, 800f, 450f, 800f, 450f, contents = mutableListOf()).border(),
        Size(1200f, 800f),
    )

    // ---------- addContents ----------

    @Test
    fun `photos are appended in order and filled to the frame`() {
        val target = item("i", contents = 1)

        val added = DraftEditor.addContents(
            target,
            listOf("p2", "p3"),
            metas("p2", "p3"),
            srcSizeOf,
            ::markCheckpoint,
        )

        assertEquals(listOf("p2", "p3"), added.map { it.photoId })
        assertEquals(listOf("p1", "p2", "p3"), target.contents.map { it.photoId })
        assertEquals(listOf(0, 1, 2), target.contents.map { it.orderIndex })
        assertEquals("новое фото заполняет рамку", fillScale, added[0].contentScale, 1e-6f)
        assertEquals(0f, added[0].panX, 0f)
        assertEquals(0f, added[0].panY, 0f)
        assertEquals(1, checkpoints)
    }

    @Test
    fun `a blank frame takes photos up to the limit`() {
        val target = item("i")

        val added = DraftEditor.addContents(
            target,
            List(Const.MAX_CONTENTS_PER_ITEM) { "p$it" },
            metas(*Array(Const.MAX_CONTENTS_PER_ITEM) { "p$it" }),
            srcSizeOf,
            ::markCheckpoint,
        )

        assertEquals(Const.MAX_CONTENTS_PER_ITEM, added.size)
        assertEquals(Const.MAX_CONTENTS_PER_ITEM, target.contents.size)
    }

    /** Дубликат — то же фото, что уже в рамке; второй раз оно не нужно и места не занимает. */
    @Test
    fun `duplicates are filtered out`() {
        val target = item("i", contents = 2)

        val added = DraftEditor.addContents(
            target, listOf("p2", "p3"), metas("p3"), srcSizeOf, ::markCheckpoint,
        )

        assertEquals(listOf("p3"), added.map { it.photoId })
        assertEquals(3, target.contents.size)
        assertEquals(1, checkpoints)
    }

    @Test
    fun `the per item limit is respected`() {
        val target = item("i", contents = Const.MAX_CONTENTS_PER_ITEM)

        val added = DraftEditor.addContents(
            target, listOf("p9"), metas("p9"), srcSizeOf, ::markCheckpoint,
        )

        assertTrue("рамка уже полна", added.isEmpty())
        assertEquals(Const.MAX_CONTENTS_PER_ITEM, target.contents.size)
        assertEquals("лимит не превышен — checkpoint ставить не на что", 0, checkpoints)
    }

    /** Порядок фильтров: дубликаты отсекаются ДО лимита, иначе они бы заняли его место. */
    @Test
    fun `duplicates do not eat the remaining capacity`() {
        val target = item("i", contents = 4)

        val added = DraftEditor.addContents(
            target,
            listOf("p1", "p5", "p6"),
            metas("p1", "p5", "p6"),
            srcSizeOf,
            ::markCheckpoint,
        )

        assertEquals("свободно ровно одно место, и его занимает не дубликат", listOf("p5"), added.map { it.photoId })
    }

    @Test
    fun `the request is cut to the remaining capacity`() {
        val target = item("i", contents = 3)

        val added = DraftEditor.addContents(
            target,
            listOf("p4", "p5", "p6", "p7"),
            metas("p4", "p5", "p6", "p7"),
            srcSizeOf,
            ::markCheckpoint,
        )

        assertEquals(listOf("p4", "p5"), added.map { it.photoId })
        assertEquals(Const.MAX_CONTENTS_PER_ITEM, target.contents.size)
    }

    /** Один и тот же id в одном запросе добавляется в рамку один раз. */
    @Test
    fun `duplicate ids inside one request are added once`() {
        val target = item("i", contents = 1)

        val added = DraftEditor.addContents(
            target,
            listOf("p2", "p2", "p3"),
            metas("p2", "p3"),
            srcSizeOf,
            ::markCheckpoint,
        )

        assertEquals(listOf("p2", "p3"), added.map { it.photoId })
        assertEquals(listOf("p1", "p2", "p3"), target.contents.map { it.photoId })
        assertEquals(1, checkpoints)
    }

    /**
     * Граница, на которой разошлись бы два способа убрать дубли. Сначала запрос режется по
     * свободному месту (здесь их два: `[p4, p4]`), и только потом дубли схлопываются, поэтому
     * `p5` не попадает в рамку. Тест закрепляет этот порядок, чтобы он не менялся незаметно.
     */
    @Test
    fun `duplicates inside a request are collapsed after the capacity cut`() {
        val target = item("i", contents = 3)

        val added = DraftEditor.addContents(
            target,
            listOf("p4", "p4", "p5"),
            metas("p4", "p5"),
            srcSizeOf,
            ::markCheckpoint,
        )

        assertEquals(listOf("p4"), added.map { it.photoId })
        assertEquals(4, target.contents.size)
    }

    @Test
    fun `an empty request changes nothing`() {
        val target = item("i", contents = 1)

        val added = DraftEditor.addContents(target, emptyList(), metas("p1"), srcSizeOf, ::markCheckpoint)

        assertTrue(added.isEmpty())
        assertEquals(1, target.contents.size)
        assertEquals(0, checkpoints)
    }

    /** Без метаданных фото нечем ни построить кадр, ни посчитать размер для пресетов. */
    @Test
    fun `photos without metadata are skipped`() {
        val target = item("i")

        val added = DraftEditor.addContents(
            target, listOf("pX"), emptyMap(), srcSizeOf, ::markCheckpoint,
        )

        assertTrue(added.isEmpty())
        assertEquals(0, target.contents.size)
        assertEquals(0, checkpoints)
    }

    @Test
    fun `only the requested photos with metadata are added`() {
        val target = item("i")

        val added = DraftEditor.addContents(
            target, listOf("p2", "pX"), metas("p2"), srcSizeOf, ::markCheckpoint,
        )

        assertEquals(listOf("p2"), added.map { it.photoId })
        assertEquals(1, checkpoints)
    }

    /** `beforeApply` — точка ДО первой мутации, а не после неё. */
    @Test
    fun `checkpoint is requested before the first mutation`() {
        val target = item("i", contents = 1)
        var contentsAtCheckpoint = -1

        DraftEditor.addContents(
            target, listOf("p2"), metas("p2"), srcSizeOf,
        ) { contentsAtCheckpoint = target.contents.size }

        assertEquals("в момент checkpoint набор фото ещё прежний", 1, contentsAtCheckpoint)
        assertEquals(2, target.contents.size)
    }

    // ---------- removeActiveContent ----------

    @Test
    fun `the last photo cannot be removed`() {
        val target = item("i", contents = 1)

        val idx = DraftEditor.removeActiveContent(target, 0, ::markCheckpoint)

        assertNull("рамка без фото — это уже другая сущность", idx)
        assertEquals(1, target.contents.size)
        assertEquals(0, checkpoints)
    }

    /** На рамке без фото зажимание индекса в диапазон `0 .. -1` бросило бы исключение. */
    @Test
    fun `an empty frame is left alone`() {
        val target = item("i")

        val idx = DraftEditor.removeActiveContent(target, 0, ::markCheckpoint)

        assertNull(idx)
        assertEquals(0, target.contents.size)
        assertEquals(0, checkpoints)
    }

    @Test
    fun `removing a middle photo selects the previous one and renumbers`() {
        val target = item("i", contents = 3)

        val idx = DraftEditor.removeActiveContent(target, 1, ::markCheckpoint)

        assertEquals("активным становится предыдущее фото", 0, idx!!)
        assertEquals(listOf("p1", "p3"), target.contents.map { it.photoId })
        assertEquals(listOf(0, 1), target.contents.map { it.orderIndex })
        assertEquals(1, checkpoints)
    }

    @Test
    fun `removing the last photo selects the last of the rest`() {
        val target = item("i", contents = 3)

        val idx = DraftEditor.removeActiveContent(target, 2, ::markCheckpoint)

        assertEquals(1, idx!!)
        assertEquals(listOf("p1", "p2"), target.contents.map { it.photoId })
    }

    @Test
    fun `removing the first photo selects index zero`() {
        val target = item("i", contents = 2)

        val idx = DraftEditor.removeActiveContent(target, 0, ::markCheckpoint)

        assertEquals(0, idx!!)
        assertEquals(listOf("p2"), target.contents.map { it.photoId })
        assertEquals(listOf(0), target.contents.map { it.orderIndex })
    }

    /** Индекс в состоянии вправе устареть после restore — зажимаем, а не падаем. */
    @Test
    fun `an index out of range is clamped`() {
        val target = item("i", contents = 3)

        val idx = DraftEditor.removeActiveContent(target, 99, ::markCheckpoint)

        assertEquals("удалён последний, активен предыдущий", 1, idx!!)
        assertEquals(listOf("p1", "p2"), target.contents.map { it.photoId })
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
        val target = item("i", contents = 1)

        assertTrue(DraftEditor.applyPreset(target, 0, FitPreset.FILL, srcSizeOf, { photo }, ::markCheckpoint))

        assertEquals(fillScale, target.contents[0].contentScale, 1e-6f)
        assertEquals(0f, target.contents[0].panX, 0f)
        assertEquals(0f, target.contents[0].panY, 0f)
        assertEquals(1, checkpoints)
    }

    @Test
    fun `the FIT preset fits the whole photo`() {
        val target = item("i", contents = 1)

        assertTrue(DraftEditor.applyPreset(target, 0, FitPreset.FIT, srcSizeOf, { photo }, ::markCheckpoint))

        assertEquals(fitScale, target.contents[0].contentScale, 1e-6f)
        assertNotEquals("fit и fill обязаны различаться на этой геометрии", fillScale, target.contents[0].contentScale)
    }

    /**
     * Пресет — явный выбор пользователя по конкретному фото, и он важнее блокировки FIT:
     * режим становится FREE. Остальные фото рамки при этом не скачкуют — для них вписанные
     * значения уже запечены.
     */
    @Test
    fun `a preset in FIT leaves FREE and does not jump the other photos`() {
        val target = item("i", contents = 2, framingMode = FramingMode.FIT)

        assertTrue(DraftEditor.applyPreset(target, 0, FitPreset.FILL, srcSizeOf, { photo }, ::markCheckpoint))

        assertEquals(FramingMode.FREE, target.framingMode)
        assertEquals(fillScale, target.contents[0].contentScale, 1e-6f)
        assertEquals(
            "неактивное фото сохранило вписанный масштаб, а не устаревший",
            fitScale,
            target.contents[1].contentScale,
            1e-6f,
        )
    }

    @Test
    fun `a preset without an active photo changes nothing`() {
        val target = item("i")

        assertFalse(DraftEditor.applyPreset(target, 0, FitPreset.FILL, srcSizeOf, { photo }, ::markCheckpoint))
        assertEquals(0, checkpoints)
    }

    /** Без размера фото пресет считать нечем — тихо ничего не меняем, а не ставим пустой шаг. */
    @Test
    fun `a preset for a photo of unknown size changes nothing`() {
        val target = item("i", contents = 1)

        assertFalse(DraftEditor.applyPreset(target, 0, FitPreset.FILL, { null }, { photo }, ::markCheckpoint))
        assertEquals(1f, target.contents[0].contentScale, 0f)
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
        val target = item("i", contents = 1, framingMode = FramingMode.FIT)
        target.contents[0].contentScale = 99f
        target.contents[0].panX = 33f

        assertTrue(DraftEditor.applyFramingMode(target, FramingMode.FREE) { photo })

        assertEquals(FramingMode.FREE, target.framingMode)
        assertEquals(fitScale, target.contents[0].contentScale, 1e-6f)
        assertEquals("в FIT pan всегда нулевой", 0f, target.contents[0].panX, 0f)
        assertEquals(0f, target.contents[0].panY, 0f)
    }

    /** На входе в FIT сохранённые значения не трогаем: кнопка AutoFit вернёт пользователю именно их. */
    @Test
    fun `entering FIT keeps the stored values`() {
        val target = item("i", contents = 1)
        target.contents[0].contentScale = 2.25f
        target.contents[0].panX = -14f

        assertTrue(DraftEditor.applyFramingMode(target, FramingMode.FIT) { photo })

        assertEquals(FramingMode.FIT, target.framingMode)
        assertEquals(2.25f, target.contents[0].contentScale, 0f)
        assertEquals(-14f, target.contents[0].panX, 0f)
    }

    @Test
    fun `switching to the same mode changes nothing`() {
        val target = item("i", contents = 1, framingMode = FramingMode.FIT)
        target.contents[0].contentScale = 7f
        target.contents[0].panX = -5f

        assertFalse("FIT → FIT ничего не меняет", DraftEditor.applyFramingMode(target, FramingMode.FIT) { photo })
        assertEquals("FIT → FIT не должен запекать", 7f, target.contents[0].contentScale, 0f)
        assertEquals(-5f, target.contents[0].panX, 0f)

        val free = item("j", contents = 1, framingMode = FramingMode.FREE)
        free.contents[0].contentScale = 3f
        assertFalse("FREE → FREE ничего не меняет", DraftEditor.applyFramingMode(free, FramingMode.FREE) { photo })
        assertEquals(3f, free.contents[0].contentScale, 0f)
    }

    /** Фото без метаданных при запекании пропускается, а не ломает весь проход. */
    @Test
    fun `a photo without metadata is skipped while baking`() {
        val target = item("i", contents = 2, framingMode = FramingMode.FIT)
        target.contents[0].contentScale = 7f
        target.contents[1].contentScale = 8f

        assertTrue(DraftEditor.applyFramingMode(target, FramingMode.FREE) { if (it == "p1") photo else null })

        assertEquals(fitScale, target.contents[0].contentScale, 1e-6f)
        assertEquals("фото без метаданных сохраняет то, что было", 8f, target.contents[1].contentScale, 0f)
    }

    // ---------- newComboDrafts ----------

    /**
     * Старт «с нуля» — не тупик: пустая рамка на весь холст, сразу активная и выделенная,
     * иначе билдер остался бы полностью нефункциональным (все кнопки тулбара требуют
     * выделенной рамки).
     */
    @Test
    fun `no photos means one blank frame on the whole canvas`() {
        val new = DraftEditor.newComboDrafts(emptyList(), 1600f, 900f)

        val only = new.items.single()
        assertEquals(0, only.contents.size)
        assertEquals(1600f, only.width, 0f)
        assertEquals(900f, only.height, 0f)
        assertEquals(800f, only.centerX, 0f)
        assertEquals(450f, only.centerY, 0f)
        assertEquals(FramingMode.FREE, only.framingMode)
        assertEquals(only.id, new.selectedItemId)
        assertEquals(mapOf(only.id to 0), new.activeIndex)
    }

    @Test
    fun `photos are laid out in cells and filled`() {
        val new = DraftEditor.newComboDrafts(List(3) { photoEntity("p$it") }, 1600f, 900f)

        assertEquals(3, new.items.size)
        assertEquals(800f, new.items[1].centerX, 1e-3f)
        assertEquals(1, new.items[0].contents.size)
        assertEquals("p0", new.items[0].contents[0].photoId)
        assertEquals(
            "ячейка заполняется по её собственным размерам",
            TransformMath.fillScale(new.items[0].border(), Size(1200f, 800f)),
            new.items[0].contents[0].contentScale,
            1e-4f,
        )
        assertEquals(listOf(0, 1, 2), new.items.map { it.zIndex })
        assertEquals(new.items.map { it.id to 0 }, new.activeIndex.toList())
        assertEquals(new.items.first().id, new.selectedItemId)
    }

    /** Комбо длиннее [Const.MAX_ITEMS] рамок всё равно не создать — лимит живёт в редакторе. */
    @Test
    fun `more photos than the item limit are cut off`() {
        val new = DraftEditor.newComboDrafts(List(Const.MAX_ITEMS + 2) { photoEntity("p$it") }, 1600f, 900f)

        assertEquals(Const.MAX_ITEMS, new.items.size)
        assertEquals(Const.MAX_ITEMS, new.items.map { it.contents[0].photoId }.distinct().size)
        assertEquals("zIndex перенумеровывается подряд", (0 until Const.MAX_ITEMS).toList(), new.items.map { it.zIndex })
    }

    /** Одно фото — одна рамка во весь холст, а не крошечная ячейка. */
    @Test
    fun `a single photo takes the whole canvas`() {
        val new = DraftEditor.newComboDrafts(listOf(photo), 1600f, 900f)

        val only = new.items.single()
        assertEquals(1600f, only.width, 0f)
        assertEquals(900f, only.height, 0f)
        assertEquals(0, only.contents[0].orderIndex)
    }
}