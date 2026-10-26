package com.combo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.combo.feature.combo_list.ComboListContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.combo.ImageFixtures
import com.combo.data.ComboWithItems
import com.combo.domain.BitmapSource
import com.combo.feature.combo_list.ComboListScreen
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.feature.combo_list.NEW_COMBO_TAG
import com.combo.ui.common.PcDesktopFonts
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.common.designsystem.tokens.PcMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Реальный экран и drawCombo на синтетических данных; масштаб относится только к сцене. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class ComboListDesignSystemTest(
    private val width: Int,
    private val sceneDensity: Float,
    private val fontScale: Float,
) : UiTestBase() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "width={0}, density={1}, fontScale={2}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(420, 1f, 1f), arrayOf(1200, 1f, 1f),
            arrayOf(600, 1f, 1.25f), arrayOf(420, 1.5f, 1.25f),
        )
    }

    @Test
    fun previewsAndCommandsRemainUsableAcrossWidthsAndTextScale() = runSkikoComposeUiTest(
        Size(width * sceneDensity, (if (width == 1200) 980 else 760) * sceneDensity), density = Density(sceneDensity, fontScale),
    ) {
        val combos = gridCombos()
        val repository = FakeComboRepository(combos)
        val requests = mutableListOf<Pair<String, Int>>()
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int) =
                gridBitmap(photoId.removePrefix("p").toInt()).also { requests += photoId to requiredPx }
        }
        val vm = ComboListViewModel(repository, FakePhotoRepository((1..6).map { photoEntity("p$it").copy(width = 320, height = 220) }), source, settingsRepository = com.combo.ui.FakeGallerySettingsRepository())
        var created = 0
        val opened = mutableListOf<String>()
        setContent {
            PcDesktopTheme(motion = PcMotion(reducedMotion = true)) {
                ComboListScreen({ opened += it }, { created++ }, vm)
            }
        }
        waitUntil { vm.state.value.images.keys.containsAll(listOf("p1", "p2")) }
        waitForIdle()
        val first = onNodeWithTag("combo_preview_c1").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag("combo_preview_c2").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, second.width, 1f)
        if (width >= 600) {
            assertEquals(first.top, second.top, 1f)
            assertEquals(first.height, second.height, 1f)
            assertTrue(second.left > first.right)
        } else {
            assertTrue(second.top > first.bottom)
        }
        onNodeWithText("Combo c1").assertDoesNotExist()
        val output = File("build/ui-previews/combo-list").apply { mkdirs() }
        val prefix = "combo-list-$width-$sceneDensity-$fontScale"
        fun capture(suffix: String) {
            File(output, "$prefix-$suffix.png").writeBytes(
                ImageFixtures.pngBytes(onNodeWithTag("combo_list").captureToImage()))
        }
        capture("populated")
        onNodeWithTag("gallery_size_slider").performMouseInput { click(androidx.compose.ui.geometry.Offset(.5f, center.y)) }
        capture("small")
        onNodeWithTag("gallery_size_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        capture("large")
        onNodeWithTag("gallery_size_reset").performClick()
        assertEquals(100f, vm.galleryScale.state.value.percent)
        onNodeWithTag("combo_preview_c2").performScrollTo().performClick()
        assertEquals(listOf("c2"), opened)
        opened.clear()

        val create = onNodeWithTag(NEW_COMBO_TAG)
        create.performMouseInput { moveTo(center); press(); moveTo(topLeft - androidx.compose.ui.geometry.Offset(20f, 20f)); release() }
        assertEquals(0, created)
        create.performMouseInput { moveTo(center); click() }
        assertEquals(1, created)
        val edit = onNodeWithTag("combo_edit_c1")
        edit.performScrollTo().assertIsDisplayed().requestFocus().assertIsFocused()
        edit.performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("c1"), opened)
        capture("focus")
        onNodeWithTag("combo_delete_c1").performClick()
        waitUntil { repository.state.value.size == 5 }
        onNodeWithTag("combo_card_c1").assertDoesNotExist()
        onNodeWithTag("combo_card_c2").assertIsDisplayed()
        onNodeWithTag("combo_edit_c2").performClick()
        assertEquals(listOf("c1", "c2"), opened)
        // Удаление перестраивает состав строк; остальные команды остаются доступны.
        for (index in 2..6) onNodeWithTag("combo_delete_c$index").performScrollTo().performClick()
        waitUntil { repository.state.value.isEmpty() }
        assertEquals((1..6).map { "p$it" to 800 }.toSet(), requests.toSet())
        assertEquals(6, requests.size)
        onNodeWithText("No combos yet").assertIsDisplayed()
        create.assertIsDisplayed().performClick()
        assertEquals(2, created)
        capture("empty")
        File(output, "$prefix.txt").writeText(
            "Skiko desktop; ${width}x${if (width == 1200) 980 else 760} dp; density=$sceneDensity; fontScale=$fontScale; font=${PcDesktopFonts.resolvedFamily}\n")
    }
}

private fun gridCombos(): List<ComboWithItems> = listOf(
    1600 to 900, 1200 to 900, 1800 to 1200, 900 to 1200, 1200 to 1200, 1800 to 1000,
).mapIndexed { index, (width, height) ->
    val id = "c${index + 1}"
    ComboWithItems(comboEntity(id).copy(canvasWidth = width, canvasHeight = height), listOf(
        comboItemEntity("i$index", id, photoId = "p${index + 1}").copy(
            centerX = width / 2f, centerY = height / 2f, width = width.toFloat(), height = height.toFloat(),
            framingMode = "FREE", contentScale = maxOf(width / 320f, height / 220f),
        ),
    ))
}

/** Пейзажи-фикстуры позволяют оценить матовые поля и пропорции настоящего drawCombo. */
private fun gridBitmap(index: Int) = ImageFixtures.bitmap(320, 220) { canvas ->
    val palettes = listOf(
        listOf(0xFF819B9D, 0xFFCFBE9B, 0xFF3D575D),
        listOf(0xFF91ADA9, 0xFFE1CFAA, 0xFF497674),
        listOf(0xFF8F9D89, 0xFFD9C5A0, 0xFF3D5748),
        listOf(0xFFB49A82, 0xFFE2CCAD, 0xFF765746),
    )
    val colors = palettes[(index - 1) % palettes.size].map { Color(it) }
    canvas.drawRect(0f, 0f, 320f, 220f, Paint().apply { color = colors[0] })
    canvas.drawCircle(androidx.compose.ui.geometry.Offset(250f, 46f), 22f, Paint().apply { color = colors[1] })
    val mountain = Path().apply {
        moveTo(0f, 150f); lineTo(65f, 85f); lineTo(115f, 130f); lineTo(190f, 40f)
        lineTo(255f, 115f); lineTo(320f, 80f); lineTo(320f, 220f); lineTo(0f, 220f); close()
    }
    canvas.drawPath(mountain, Paint().apply { color = colors[2] })
    canvas.drawRect(0f, 180f, 320f, 220f, Paint().apply { color = colors[0].copy(alpha = .5f) })
}

@OptIn(ExperimentalTestApi::class)
class ComboGridResizeTest {
    @Test
    fun resizeRegroupsMixedFormatsAndKeepsRowHeightsAligned() = runSkikoComposeUiTest(Size(1200f, 900f)) {
        val width = mutableStateOf(420.dp)
        setContent { PcDesktopTheme {
            Box(Modifier.width(width.value).fillMaxSize()) {
                ComboListContent(gridCombos(), {}, {}, {}) { _, modifier -> Box(modifier.background(Color(0xFF547C80))) }
            }
        } }
        fun bounds(id: Int) = onNodeWithTag("combo_preview_c$id").fetchSemanticsNode().boundsInRoot
        val narrowFirst = bounds(1)
        assertTrue(bounds(2).top > narrowFirst.bottom)
        runOnIdle { width.value = 528.dp }
        waitForIdle()
        assertEquals(bounds(1).top, bounds(2).top, 1f)
        assertTrue(bounds(3).top > bounds(1).bottom)
        runOnIdle { width.value = 320.dp }
        waitForIdle()
        assertTrue(bounds(2).top > bounds(1).bottom)
        runOnIdle { width.value = 900.dp }
        waitForIdle()
        val wideFirst = bounds(1)
        assertEquals(wideFirst.top, bounds(3).top, 1f)
        assertEquals(wideFirst.height, bounds(2).height, 1f)
        assertEquals(wideFirst.height, bounds(3).height, 1f)
    }
}
