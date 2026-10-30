package com.combo.feature.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.combo.core.render.ItemDraft
import com.combo.core.model.FramingMode
import com.combo.ui.photoEntity
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.common.PcDesktopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Геометрия групп инспектора и однократные команды без второго источника состояния. */
@OptIn(ExperimentalTestApi::class)
class BuilderInspectorCompositionTest {
    @Test
    fun photoSizeTracksSelectedFrameAndEffectiveFit() = runSkikoComposeUiTest(Size(400f, 800f)) {
        val first = ItemDraft("first", 0, 0f, 0f, 400f, 300f, photoId = "p1", contentScale = 2f, panX = 0f, panY = 0f)
        val second = ItemDraft("second", 1, 0f, 0f, 300f, 300f, photoId = "p2", contentScale = 3f, panX = 0f, panY = 0f, framingMode = FramingMode.FIT)
        var current by mutableStateOf(BuilderUiState(items = listOf(first, second), selectedItemId = "first", photos = mapOf(
            "p1" to photoEntity("p1").copy(width = 800, height = 600),
            "p2" to photoEntity("p2").copy(width = 1200, height = 600),
        )))
        setContent { PcDesktopTheme { BuilderInspector(current, wide = true, onEvent = {}, photoSize = current.selectedPhotoSize(), onReplace = {}) } }
        onNodeWithText("1600 × 1200 px").assertIsDisplayed()
        onNodeWithText("Original 800 × 600 px").assertIsDisplayed()
        onNodeWithText("Photo size · 200%").assertIsDisplayed()
        runOnIdle { current = current.copy(selectedItemId = "second") }
        onNodeWithText("300 × 150 px").assertIsDisplayed()
        onNodeWithText("Photo size · 25%").assertIsDisplayed()
        onNodeWithText("1600 × 1200 px").assertDoesNotExist()
        runOnIdle { current = current.copy(selectedItemId = null) }
        onNodeWithText("300 × 150 px").assertDoesNotExist()
    }

    private val state = BuilderUiState(
        items = (0..2).map { index ->
            ItemDraft("frame-$index", index, 0f, 0f, 100f, 100f,
                photoId = "photo-$index", contentScale = 1f, panX = 0f, panY = 0f)
        },
        selectedItemId = "frame-1",
    )

    @Test
    fun wideInspectorUsesFullWidthPlacementAndOneCompactActionRow() = runSkikoComposeUiTest(Size(240f, 600f), density = Density(1f)) {
        val events = mutableListOf<BuilderEvent>()
        var replacements = 0
        setContent {
            PcDesktopTheme {
                Box(Modifier.width(240.dp)) {
                    BuilderInspector(state, wide = true, onEvent = events::add, onReplace = { replacements++ })
                }
            }
        }
        fun bounds(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val frame = bounds(TOOL_TAG_MODE_FRAME)
        val photo = bounds(TOOL_TAG_MODE_PHOTO)
        val fit = bounds(TOOL_TAG_FIT)
        val fill = bounds(TOOL_TAG_FILL)
        assertEquals(16f, fit.left, 1f)
        assertEquals(224f, fill.right, 1f)
        assertEquals(fit.width, fill.width, 1f)
        assertEquals(3f, frame.left - fit.left, 0.01f)
        assertEquals(3f, fill.right - photo.right, 0.01f)
        val auto = bounds(TOOL_TAG_AUTOFIT)
        assertEquals(fit.left, auto.left, 1f)
        val autoLabel = onNodeWithText("Always fit", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(fit.left + 32f, autoLabel.left, 1f)
        assertTrue("флажок расположен под размещением", auto.top >= fit.bottom)
        assertTrue("флажок не растянут с центрированием", auto.width < fit.width + fill.width)
        val actions = listOf(
            bounds(TOOL_TAG_REPLACE_PHOTO),
            onNodeWithContentDescription("Move one layer up").fetchSemanticsNode().boundsInRoot,
            onNodeWithContentDescription("Move one layer down").fetchSemanticsNode().boundsInRoot,
            bounds(TOOL_TAG_DEL),
        )
        actions.forEach {
            assertEquals(actions.first().top, it.top, 1f)
            assertTrue(it.width >= 34f && it.height >= 34f)
            assertTrue(it.left >= 16f && it.right <= 224f)
        }
        actions.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        onNodeWithTag(TOOL_TAG_FIT).performClick()
        onNodeWithTag(TOOL_TAG_FILL).performClick()
        onNodeWithTag(TOOL_TAG_REPLACE_PHOTO).performClick()
        onNodeWithContentDescription("Move one layer up").performClick()
        onNodeWithContentDescription("Move one layer down").performClick()
        onNodeWithTag(TOOL_TAG_DEL).performClick()
        assertEquals(1, replacements)
        assertEquals(listOf(
            BuilderEvent.ApplyPreset("frame-1", FitPreset.FIT),
            BuilderEvent.ApplyPreset("frame-1", FitPreset.FILL),
            BuilderEvent.ReorderItem("frame-1", true),
            BuilderEvent.ReorderItem("frame-1", false),
            BuilderEvent.DeleteItem("frame-1"),
        ), events)
    }

    @Test
    fun wideInspectorKeepsActionsWithinItsWidthAtLargerTextScale() = runSkikoComposeUiTest(Size(240f, 600f), density = Density(1f)) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.25f)) {
                PcDesktopTheme {
                    Box(Modifier.width(240.dp)) {
                        BuilderInspector(state, wide = true, onEvent = {}, onReplace = {})
                    }
                }
            }
        }
        val actions = listOf(
            onNodeWithTag(TOOL_TAG_REPLACE_PHOTO),
            onNodeWithContentDescription("Move one layer up"),
            onNodeWithContentDescription("Move one layer down"),
            onNodeWithTag(TOOL_TAG_DEL),
        ).map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        actions.forEach {
            assertTrue(it.left >= 16f && it.right <= 224f)
            assertTrue(it.width >= 34f && it.height >= 34f)
        }
        actions.zipWithNext().forEach { (left, right) ->
            assertTrue("перенос сохраняет раздельные области ввода", right.top >= left.bottom || right.left >= left.right)
        }
    }

    @Test
    fun compactInspectorWrapsGroupsWithAllActionsReachableAtLargerTextScale() = runSkikoComposeUiTest(Size(420f, 350f), density = Density(1f)) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.25f)) {
                PcDesktopTheme {
                    Box(Modifier.width(420.dp)) {
                        BuilderInspector(state, wide = false, onEvent = {}, onReplace = {})
                    }
                }
            }
        }
        val bounds = listOf(TOOL_TAG_MODE_FRAME, TOOL_TAG_MODE_PHOTO, TOOL_TAG_FIT,
            TOOL_TAG_FILL, TOOL_TAG_AUTOFIT, TOOL_TAG_REPLACE_PHOTO, TOOL_TAG_DEL).map {
            onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach {
            assertTrue("команды внутри компактной панели", Rect(0f, 0f, 420f, 260f).contains(it.topLeft) && it.right <= 420f && it.bottom <= 260f)
            assertTrue(it.width >= 34f && it.height >= 34f)
        }
    }
}
