package com.combo

import com.combo.core.background.AverageAreaOklabStrategy
import com.combo.core.background.BackgroundInput
import com.combo.core.background.BackgroundPresets
import com.combo.core.background.BackgroundResolver
import com.combo.core.background.BackgroundSpec
import com.combo.core.background.BackgroundStrategy
import com.combo.core.background.FrameInput
import com.combo.core.background.ResolvedBackground
import com.combo.core.render.Border
import com.combo.core.render.comboBackgroundOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Поканальное сравнение цветов: упакованный Int сравнивать нельзя — один канал
 * «уплывает» и сравнение проходит/падает не по тому поводу.
 */
fun assertArgbEquals(expected: Triple<Int, Int, Int>, actual: Int, tolPerChannel: Int = 2) {
    val got = Triple(actual shr 16 and 0xFF, actual shr 8 and 0xFF, actual and 0xFF)
    val tol = tolPerChannel.toDouble()
    assertEquals("R: ожидалось ${expected.first}, получено ${got.first}", expected.first.toDouble(), got.first.toDouble(), tol)
    assertEquals("G: ожидалось ${expected.second}, получено ${got.second}", expected.second.toDouble(), got.second.toDouble(), tol)
    assertEquals("B: ожидалось ${expected.third}, получено ${got.third}", expected.third.toDouble(), got.third.toDouble(), tol)
}

private const val RED = 0xFFFF0000.toInt()
private const val GREEN = 0xFF00FF00.toInt()
private const val BLUE = 0xFF0000FF.toInt()

class BackgroundStrategyTest {

    private fun resolve(
        frames: List<FrameInput>,
        canvasW: Float = 100f,
        canvasH: Float = 100f,
    ): Int = (AverageAreaOklabStrategy.resolve(BackgroundInput(canvasW, canvasH, frames))
        as ResolvedBackground.Solid).argb

    private fun frame(l: Float, t: Float, r: Float, b: Float, vararg colors: Int) =
        FrameInput(l, t, r, b, colors.toList())

    @Test
    fun `single photo over whole canvas resolves to its own color`() {
        assertArgbEquals(Triple(255, 0, 0), resolve(listOf(frame(0f, 0f, 100f, 100f, RED))))
    }

    /** В sRGB среднее красного и синего — (128, 0, 128); в OKLab — (140, 83, 162). */
    @Test
    fun `red and blue average is oklab average, not srgb`() {
        val result = resolve(
            listOf(
                frame(0f, 0f, 50f, 100f, RED),
                frame(50f, 0f, 100f, 100f, BLUE),
            ),
        )
        assertArgbEquals(Triple(140, 83, 162), result, tolPerChannel = 1)
        assertNotEquals("это не среднее sRGB", 0x800080, result)
    }

    @Test
    fun `weight is proportional to visible area`() {
        // Красного 3:1 по площади против синего -> (198, 73, 109);
        // при равных весах было бы (140, 83, 162), а в sRGB — (191, 0, 64).
        val result = resolve(
            listOf(
                frame(0f, 0f, 75f, 100f, RED),
                frame(75f, 0f, 100f, 100f, BLUE),
            ),
        )
        assertArgbEquals(Triple(198, 73, 109), result, tolPerChannel = 1)
    }

    @Test
    fun `frame partially off canvas counts only visible part`() {
        // Половина красной рамки за холстом: её вес равен красной рамке во весь холст,
        // то есть итог — то же равное смешение, что и у 1:1 (208, 168, 0).
        val halfOffscreen = resolve(
            listOf(frame(-50f, 0f, 50f, 100f, RED), frame(50f, 0f, 100f, 100f, GREEN)),
        )
        val sameVisibleArea = resolve(
            listOf(frame(0f, 0f, 50f, 100f, RED), frame(50f, 0f, 100f, 100f, GREEN)),
        )
        assertEquals(
            "учитывается только видимая часть рамки",
            sameVisibleArea,
            halfOffscreen,
        )
        assertArgbEquals(Triple(208, 168, 0), halfOffscreen, tolPerChannel = 1)
    }

    @Test
    fun `frame completely off canvas does not affect result`() {
        val withOffscreen = resolve(
            listOf(
                frame(0f, 0f, 100f, 100f, BLUE),
                frame(200f, 200f, 300f, 300f, RED),
            ),
        )
        assertArgbEquals(Triple(0, 0, 255), withOffscreen, tolPerChannel = 1)
    }

    /**
     * Учитываются ВСЕ фото рамки, иначе фон мигал бы при смене активного фото.
     * Пара специально несимметричная: рамка A — одно красное фото, рамка B — два зелёных.
     * Без деления веса на число фото красное весило бы столько же, сколько каждое из
     * зелёных, и результат был бы (179, 199, 0) вместо (208, 168, 0).
     */
    @Test
    fun `all photos of a frame share its weight equally`() {
        val result = resolve(
            listOf(
                frame(0f, 0f, 100f, 100f, RED),
                frame(0f, 0f, 100f, 100f, GREEN, GREEN),
            ),
        )
        assertArgbEquals(Triple(208, 168, 0), result, tolPerChannel = 1)
    }

    @Test
    fun `empty input resolves to fallback background`() {
        assertEquals(Const.FALLBACK_BACKGROUND, resolve(emptyList()))
        // Есть рамка, но фото без метаданных — тоже fallback.
        assertEquals(Const.FALLBACK_BACKGROUND, resolve(listOf(frame(0f, 0f, 10f, 10f))))
    }

    @Test
    fun `oklab round trip is stable for black white and gray`() {
        for (argb in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF3C3C3C.toInt())) {
            val (l, a, b) = AverageAreaOklabStrategy.oklab(argb)
            val back = AverageAreaOklabStrategy.fromOklab(l, a, b)
            assertEquals("красный канал $argb", (argb shr 16 and 0xFF).toDouble(), (back shr 16 and 0xFF).toDouble(), 1.0)
            assertEquals("зелёный канал $argb", (argb shr 8 and 0xFF).toDouble(), (back shr 8 and 0xFF).toDouble(), 1.0)
            assertEquals("синий канал $argb", (argb and 0xFF).toDouble(), (back and 0xFF).toDouble(), 1.0)
            assertEquals("альфа всегда непрозрачная", 255.0, (back ushr 24 and 0xFF).toDouble(), 0.0)
        }
    }

    // ---------- резолвер ----------

    @Test
    fun `solid spec passes through unchanged`() {
        val argb = 0xFF123456.toInt()
        val resolved = BackgroundResolver.Default.resolve(
            BackgroundSpec.Solid(argb),
            BackgroundInput(100f, 100f, emptyList()),
        )
        assertEquals(ResolvedBackground.Solid(argb), resolved)
    }

    @Test
    fun `unknown strategy id falls back to default without throwing`() {
        val resolved = BackgroundResolver.Default.resolve(
            BackgroundSpec.Auto(strategy = "strategy.from.the.future"),
            BackgroundInput(100f, 100f, listOf(frame(0f, 0f, 100f, 100f, GREEN))),
        )
        assertArgbEquals(Triple(0, 255, 0), (resolved as ResolvedBackground.Solid).argb, tolPerChannel = 1)
    }

    @Test
    fun `resolver uses registered strategy by id`() {
        val custom = object : BackgroundStrategy {
            override val id = "custom.v1"
            override fun resolve(input: BackgroundInput) = ResolvedBackground.Solid(0xFF010203.toInt())
        }
        val resolver = BackgroundResolver(listOf(custom, AverageAreaOklabStrategy), custom)
        val input = BackgroundInput(10f, 10f, emptyList())
        assertEquals(
            ResolvedBackground.Solid(0xFF010203.toInt()),
            resolver.resolve(BackgroundSpec.Auto("custom.v1"), input),
        )
        // Неизвестный id -> дефолтная стратегия того же резолвера.
        assertEquals(
            ResolvedBackground.Solid(0xFF010203.toInt()),
            resolver.resolve(BackgroundSpec.Auto("nope"), input),
        )
    }

    // ---------- comboBackgroundOf (общий адаптер билдера/плеера/списка) ----------

    /**
     * Геометрия, а не цвет: одна рамка даёт цвет своего фото при любом положении, поэтому
     * «просто сдвинуть рамку» проверяет не вес, а цвет. Две рамки разного размера — вот
     * где вес по площади действительно участвует, и результат обязан меняться вместе с ним.
     * Рамки не перекрываются (0..75 и 75..100), иначе это была бы проверка на наложение.
     */
    @Test
    fun `comboBackgroundOf reacts to frame geometry for auto background`() {
        val even = comboBackgroundOf(
            100, 100,
            BackgroundSpec.Auto(),
            listOf(Border(25f, 50f, 50f, 100f) to listOf(RED), Border(75f, 50f, 50f, 100f) to listOf(BLUE)),
        )
        val skewed = comboBackgroundOf(
            100, 100,
            BackgroundSpec.Auto(),
            listOf(Border(37.5f, 50f, 75f, 100f) to listOf(RED), Border(87.5f, 50f, 25f, 100f) to listOf(BLUE)),
        )

        assertArgbEquals(Triple(140, 83, 162), (even as ResolvedBackground.Solid).argb, tolPerChannel = 1)
        assertArgbEquals(Triple(198, 73, 109), (skewed as ResolvedBackground.Solid).argb, tolPerChannel = 1)
        assertNotEquals(
            "смена площади рамки обязана менять Auto-фон",
            (even as ResolvedBackground.Solid).argb,
            (skewed as ResolvedBackground.Solid).argb,
        )
    }

    @Test
    fun `comboBackgroundOf returns solid color as is`() {
        val resolved = comboBackgroundOf(
            100, 100,
            BackgroundSpec.Solid(0xFF102030.toInt()),
            listOf(Border(50f, 50f, 100f, 100f) to listOf(RED)),
        ) as ResolvedBackground.Solid
        assertArgbEquals(Triple(0x10, 0x20, 0x30), resolved.argb, tolPerChannel = 0)
    }

    @Test
    fun `comboBackgroundOf falls back when there are no colors`() {
        val resolved = comboBackgroundOf(
            100, 100,
            BackgroundSpec.Auto(),
            listOf(Border(50f, 50f, 100f, 100f) to emptyList()),
        ) as ResolvedBackground.Solid
        assertEquals(Const.FALLBACK_BACKGROUND, resolved.argb)
    }

    // ---------- пресеты кнопки BG ----------

    @Test
    fun `preset cycle walks the whole circle from any state`() {
        val cycle = BackgroundPresets.cycle
        var spec: BackgroundSpec = BackgroundSpec.Solid(0xFF00FF00.toInt()) // Custom вне цикла
        val seen = mutableListOf<BackgroundSpec>()
        repeat(cycle.size) {
            spec = BackgroundPresets.next(spec)
            seen += spec
        }
        assertEquals(cycle, seen)
        // Из середины цикла тоже круг.
        assertEquals(cycle[0], BackgroundPresets.next(cycle[cycle.size - 1]))
    }

    @Test
    fun `preset labels are human readable`() {
        assertEquals("Auto", BackgroundPresets.label(BackgroundSpec.Auto()))
        assertEquals("White", BackgroundPresets.label(BackgroundSpec.Solid(0xFFFFFFFF.toInt())))
        assertEquals("Black", BackgroundPresets.label(BackgroundSpec.Solid(0xFF000000.toInt())))
        assertEquals("Gray", BackgroundPresets.label(BackgroundSpec.Solid(0xFF808080.toInt())))
        assertEquals("Custom", BackgroundPresets.label(BackgroundSpec.Solid(0xFF123456.toInt())))
    }
}