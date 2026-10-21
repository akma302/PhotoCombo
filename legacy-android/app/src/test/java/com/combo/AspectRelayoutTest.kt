package com.combo

import androidx.compose.ui.geometry.Size
import androidx.lifecycle.SavedStateHandle
import com.combo.Const
import com.combo.core.render.AspectPresets
import com.combo.core.render.Border
import com.combo.core.render.CanvasFormat
import com.combo.core.render.ContentDraft
import com.combo.core.model.FramingMode
import com.combo.core.render.ItemDraft
import com.combo.core.render.TransformMath
import com.combo.core.render.relayout
import com.combo.feature.builder.BuilderEvent
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.comboContentEntity
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Смена формата холста (соотношения сторон).
 *
 * Ключевое свойство — круг A → B → A возвращает исходную раскладку. Оно нарушается, если
 * пересчитывать фото «как получится», а не сохранять его приближение относительно
 * заполнения рамки, поэтому оно проверяется первым.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AspectRelayoutTest {

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

    private fun content(scale: Float = 1f, panX: Float = 0f, panY: Float = 0f) =
        ContentDraft("c", "p1", 0, scale, panX, panY)

    private fun item(
        id: String,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        framing: FramingMode = FramingMode.FREE,
        vararg contents: ContentDraft,
    ) = ItemDraft(
        id = id,
        zIndex = 0,
        centerX = cx,
        centerY = cy,
        width = w,
        height = h,
        framingMode = framing,
        contents = contents.toMutableList(),
    )

    /** Фото 1200×800 — как у фейков `photoEntity`. */
    private val sizes: (String) -> Size? = { Size(1200f, 800f) }

    /** Сетка 2×2: четыре рамки по четвертям холста. */
    private fun grid2x2() = listOf(
        item("a", 400f, 225f, 800f, 450f, contents = arrayOf(content())),
        item("b", 1200f, 225f, 800f, 450f, contents = arrayOf(content())),
        item("c", 400f, 675f, 800f, 450f, contents = arrayOf(content())),
        item("d", 1200f, 675f, 800f, 450f, contents = arrayOf(content())),
    )

    // ---------- пропорциональность ----------

    @Test
    fun `frames follow the canvas proportionally`() {
        val items = grid2x2()
        relayout(items, 1600f, 900f, 900f, 1600f, sizes)

        // kx = 0.5625, ky = 1.7778
        assertEquals(400f * 900f / 1600f, items[0].centerX, 1e-3f)
        assertEquals(225f * 1600f / 900f, items[0].centerY, 1e-3f)
        assertEquals(800f * 900f / 1600f, items[0].width, 1e-3f)
        assertEquals(450f * 1600f / 900f, items[0].height, 1e-3f)
    }

    /** Сетка 2×2 обязана остаться сеткой 2×2: углы холста — на углах холста. */
    @Test
    fun `a 2x2 grid stays a 2x2 grid after aspect change`() {
        val items = grid2x2()
        relayout(items, 1600f, 900f, 1200f, 1200f, sizes)

        val xs = items.map { it.centerX }.distinct().sorted()
        val ys = items.map { it.centerY }.distinct().sorted()
        assertEquals(2, xs.distinct().size)
        assertEquals(2, ys.distinct().size)
        // Левый столбец ровно на 1/4 ширины, правый на 3/4 — иначе «сетка» рассыпалась бы.
        assertEquals(300f, xs[0], 1e-3f)
        assertEquals(900f, xs[1], 1e-3f)
        assertEquals(300f, ys[0], 1e-3f)
        assertEquals(900f, ys[1], 1e-3f)
        // Четверти не наезжают друг на друга: сумма половин равна размеру.
        assertEquals(1200f, items[0].width * 2f, 1e-3f)
        assertEquals(1200f, items[0].height * 2f, 1e-3f)
    }

    /** Главное свойство: A → B → A возвращает исходные координаты. */
    @Test
    fun `A to B and back returns the original layout`() {
        val items = grid2x2()
        val before = items.map { Triple(it.border(), it.contents[0].contentScale, it.contents[0].panX) }

        relayout(items, 1600f, 900f, 900f, 1600f, sizes)
        relayout(items, 900f, 1600f, 1600f, 900f, sizes)

        items.forEachIndexed { i, it ->
            val (b, scaleBefore, panBefore) = before[i]
            assertEquals("центр X рамки $i", b.centerX, it.centerX, 1e-3f)
            assertEquals("центр Y рамки $i", b.centerY, it.centerY, 1e-3f)
            assertEquals("ширина рамки $i", b.width, it.width, 1e-3f)
            assertEquals("высота рамки $i", b.height, it.height, 1e-3f)
            assertEquals("масштаб фото $i", scaleBefore, it.contents[0].contentScale, 1e-3f)
            assertEquals("pan фото $i", panBefore, it.contents[0].panX, 1e-3f)
        }
    }

    @Test
    fun `frame size never drops below the minimum`() {
        val items = listOf(item("tiny", 20f, 20f, 10f, 10f, contents = arrayOf(content())))
        relayout(items, 1600f, 900f, 100f, 100f, sizes)

        val minSide = Const.MIN_BORDER_FRACTION * 100f
        assertEquals(minSide, items[0].width, 1e-4f)
        assertEquals(minSide, items[0].height, 1e-4f)
    }

    // ---------- содержимое рамки ----------

    /** `r = contentScale / fillScale` — приближение относительно заполнения — сохраняется. */
    @Test
    fun `photo keeps its zoom relative to fill`() {
        val border = Border(0f, 0f, 1600f, 900f)
        val fill = TransformMath.fillScale(border, Size(1200f, 800f))
        // Нарочно «отдалено» в 2 раза меньше заполнения.
        val items = listOf(item("i", 0f, 0f, 1600f, 900f, contents = arrayOf(content(scale = fill / 2f))))

        relayout(items, 1600f, 900f, 900f, 1600f, sizes)

        val newBorder = Border(0f, 0f, 900f, 1600f)
        val newFill = TransformMath.fillScale(newBorder, Size(1200f, 800f))
        assertEquals(
            "фото должно остаться ровно в полтора раза меньше заполнения",
            newFill / 2f,
            items[0].contents[0].contentScale,
            1e-3f,
        )
    }

    /**
     * Фото, закрывавшее рамку (`r >= 1`), обязано закрывать её и после смены формата:
     * иначе у пользователя на ровном месте появились бы пустые поля в его коллаже.
     */
    @Test
    fun `photo that covered the frame keeps covering it`() {
        val border = Border(0f, 0f, 400f, 400f)
        val items = listOf(
            item(
                "i", 0f, 0f, 400f, 400f,
                contents = arrayOf(content(scale = 1f, panX = 40f, panY = 25f)),
            ),
        )
        assertEquals("предусловие: фото должно закрывать рамку", 400f / 1200f * 3f, items[0].contents[0].contentScale)

        relayout(items, 1600f, 900f, 1600f, 900f, sizes)
        items[0].contents[0].panX = 40f
        items[0].contents[0].panY = 25f

        relayout(items, 1600f, 900f, 900f, 1600f, sizes)
        items[0].contents[0].panX = 40f
        items[0].contents[0].panY = 25f
        relayout(items, 900f, 1600f, 1600f, 900f, sizes)

        val it = items[0]
        val c = it.contents[0]
        val limitX = kotlin.math.abs(1200f * c.contentScale - it.width) / 2f
        val limitY = kotlin.math.abs(800f * c.contentScale - it.height) / 2f
        assertTrue("pan по X вышел за предел закрытия рамки", kotlin.math.abs(c.panX) <= limitX + 1e-3f)
        assertTrue("pan по Y вышел за предел закрытия рамки", kotlin.math.abs(c.panY) <= limitY + 1e-3f)
        assertTrue("фото должно закрывать рамку по X", 1200f * c.contentScale >= it.width - 1e-3f)
        assertTrue("фото должно закрывать рамку по Y", 800f * c.contentScale >= it.height - 1e-3f)
    }

    /** Рамка в FIT не затрагивается: фото пересчитывает вписывание само. */
    @Test
    fun `FIT frame content is left untouched`() {
        val items = listOf(
            item(
                "i", 100f, 100f, 400f, 400f, framing = FramingMode.FIT,
                contents = arrayOf(content(scale = 7f, panX = 3f, panY = -4f)),
            ),
        )
        relayout(items, 1600f, 900f, 900f, 1600f, sizes)

        assertEquals("в FIT хранимые значения не пересчитываются", 7f, items[0].contents[0].contentScale, 0f)
        assertEquals(3f, items[0].contents[0].panX, 0f)
        assertEquals(-4f, items[0].contents[0].panY, 0f)
        // Геометрия рамки при этом пересчитана.
        assertEquals(400f * 900f / 1600f, items[0].width, 1e-3f)
    }

    /** Нулевые размеры фото не должны ломать пересчёт (нет ни r, ни fill). */
    @Test
    fun `unknown photo size leaves content untouched`() {
        val items = listOf(item("i", 0f, 0f, 800f, 450f, contents = arrayOf(content(scale = 5f, panX = 1f))))
        relayout(items, 1600f, 900f, 900f, 1600f, { null })
        assertEquals(5f, items[0].contents[0].contentScale, 0f)
        assertEquals(1f, items[0].contents[0].panX, 0f)
    }

    // ---------- подписи и цикл пресетов ----------

    @Test
    fun `aspect presets cycle and fall back to the first one`() {
        assertEquals(5, AspectPresets.all.size)
        // Из каждого пресета круг возвращает в него же после полного обхода — значит цикл
        // замкнут и проходит все пять, а не зацикливается на паре.
        for (start in AspectPresets.all) {
            var cur = start
            repeat(AspectPresets.all.size) { cur = AspectPresets.next(cur) }
            assertEquals("цикл из $start должен замкнуться на себя", start, cur)
        }
        // Текущий размер не из пресетов → подпись Custom, а следующий формат первый.
        val custom = CanvasFormat(1000, 700)
        var cur = custom
        val seen = mutableListOf<CanvasFormat>()
        repeat(AspectPresets.all.size) {
            cur = AspectPresets.next(cur)
            seen += cur
        }
        assertEquals(AspectPresets.all, seen)
    }

    @Test
    fun `aspect labels come from the current size not from a local index`() {
        assertEquals("16:9", AspectPresets.label(1600, 900))
        assertEquals("9:16", AspectPresets.label(900, 1600))
        assertEquals("1:1", AspectPresets.label(1200, 1200))
        assertEquals("4:3", AspectPresets.label(1600, 1200))
        assertEquals("3:4", AspectPresets.label(1200, 1600))
        assertEquals("Custom 1000x700", AspectPresets.label(1000, 700))
    }

    // ---------- ViewModel ----------

    private fun builder(comboRepo: FakeComboRepository, comboId: String): ComboBuilderViewModel {
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("comboId" to comboId)),
            comboRepo,
            FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2"))),
            StubBitmapSource(),
        )
        settle()
        assertTrue("билдер не загрузился", vm.state.value.loaded)
        return vm
    }

    @Test
    fun `SetAspect rescales frames and undo brings them back`() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Two"),
                    items = listOf(
                        ItemWithContents(
                            comboItemEntity("i1", "c1", 0).copy(centerX = 400f, centerY = 225f, width = 800f, height = 450f),
                            listOf(comboContentEntity("ct1", "i1", "p1")),
                        ),
                        ItemWithContents(
                            comboItemEntity("i2", "c1", 1).copy(centerX = 1200f, centerY = 675f, width = 800f, height = 450f),
                            listOf(comboContentEntity("ct2", "i2", "p2")),
                        ),
                    ),
                ),
            ),
        )
        val vm = builder(comboRepo, "c1")
        val before = vm.state.value.items.map { it.border() }

        vm.onEvent(BuilderEvent.SetAspect(900, 1600))

        assertEquals(900f, vm.state.value.canvasW, 0f)
        assertEquals(1600f, vm.state.value.canvasH, 0f)
        val after = vm.state.value.items
        assertEquals(before[0].centerX * 900f / 1600f, after[0].centerX, 1e-3f)
        assertEquals(before[0].height * 1600f / 900f, after[0].height, 1e-3f)

        vm.onEvent(BuilderEvent.Undo)

        val undone = vm.state.value.items
        assertEquals(
            "формат обязан вернуться (canUndo=${vm.state.value.canUndo}, " +
                "w=${vm.state.value.canvasW}, h=${vm.state.value.canvasH})",
            1600f,
            vm.state.value.canvasW,
            0f,
        )
        assertEquals(900f, vm.state.value.canvasH, 0f)
        before.forEachIndexed { i, b ->
            assertEquals("центр X рамки $i", b.centerX, undone[i].centerX, 1e-3f)
            assertEquals("центр Y рамки $i", b.centerY, undone[i].centerY, 1e-3f)
            assertEquals("ширина рамки $i", b.width, undone[i].width, 1e-3f)
            assertEquals("высота рамки $i", b.height, undone[i].height, 1e-3f)
        }
    }
}
