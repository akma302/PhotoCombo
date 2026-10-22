package com.combo.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
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
        Size(width * sceneDensity, 760 * sceneDensity), density = Density(sceneDensity, fontScale),
    ) {
        val combos = listOf(
            ComboWithItems(comboEntity("c1", "Weekend in the mountains — a longer combo name"),
                listOf(comboItemEntity("i1", "c1"))),
            ComboWithItems(comboEntity("c2", "City details"), listOf(comboItemEntity("i2", "c2"))),
        )
        val repository = FakeComboRepository(combos)
        val requests = mutableListOf<Pair<String, Int>>()
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int) =
                ImageFixtures.patternBitmap().also { requests += photoId to requiredPx }
        }
        val vm = ComboListViewModel(repository, FakePhotoRepository(listOf(photoEntity("p1"))), source)
        var created = 0
        val opened = mutableListOf<String>()
        setContent {
            PcDesktopTheme(motion = PcMotion(reducedMotion = true)) {
                ComboListScreen({ opened += it }, { created++ }, vm)
            }
        }
        waitUntil { vm.state.value.images.size == 1 }
        waitForIdle()
        assertEquals(listOf("p1" to 800), requests)
        val output = File("build/ui-previews/combo-list").apply { mkdirs() }
        val prefix = "combo-list-$width-$sceneDensity-$fontScale"
        fun capture(suffix: String) {
            File(output, "$prefix-$suffix.png").writeBytes(
                ImageFixtures.pngBytes(onNodeWithTag("combo_list").captureToImage()))
        }
        capture("populated")

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
        waitUntil { repository.state.value.size == 1 }
        onNodeWithTag("combo_card_c1").assertDoesNotExist()
        onNodeWithText("City details").assertIsDisplayed()
        onNodeWithTag("combo_edit_c2").performClick()
        assertEquals(listOf("c1", "c2"), opened)
        onNodeWithTag("combo_delete_c2").performClick()
        waitUntil { repository.state.value.isEmpty() }
        onNodeWithText("No combos yet").assertIsDisplayed()
        create.assertIsDisplayed().performClick()
        assertEquals(2, created)
        capture("empty")
        File(output, "$prefix.txt").writeText(
            "Skiko desktop; ${width}x760 dp; density=$sceneDensity; fontScale=$fontScale; font=${PcDesktopFonts.resolvedFamily}\n")
    }
}
