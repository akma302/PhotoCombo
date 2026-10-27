package com.combo.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.theme.PcTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GalleryGridTest {
    @Test fun columnCountHandlesThresholdsAndVerySmallWidth() {
        assertEquals(1, galleryColumnCount(0, 240, 16))
        assertEquals(1, galleryColumnCount(495, 240, 16))
        assertEquals(2, galleryColumnCount(496, 240, 16))
        assertEquals(2, galleryColumnCount(497, 240, 16))
        assertEquals(4, galleryColumnCount(1152, 240, 16))
        assertEquals(8, galleryColumnCount(1152, 120, 16))
        assertEquals(2, galleryColumnCount(1152, 480, 16))
    }

    @Test fun realCellsFillWidthGrowWithinBreakpointAndRegroupAtThreshold() = runSkikoComposeUiTest(Size(1600f, 900f)) {
        var width by mutableStateOf(525.dp)
        var reference by mutableStateOf(240.dp)
        var items by mutableStateOf((1..20).toList())
        setContent { PcTheme {
            GalleryGrid(items, { it }, reference, Modifier.width(width).fillMaxHeight(),
                contentPadding = PaddingValues(16.dp), horizontalGap = 16.dp,
                previewAspectRatio = { if (it % 3 == 0) .75f else 1f },
            ) { item, geometry ->
                Box(Modifier.fillMaxWidth().height(geometry.previewHeight).testTag("cell$item"))
            }
        } }
        fun bounds(id: Int) = onNodeWithTag("cell$id").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds(2).top > bounds(1).top)
        runOnIdle { width = 528.dp }
        waitForIdle()
        assertEquals(bounds(1).top, bounds(2).top, 1f)
        assertEquals(512f, bounds(2).right, 1f)
        val before = bounds(1).width
        runOnIdle { width = 600.dp }
        waitForIdle()
        assertTrue(bounds(1).width > before)
        assertEquals(584f, bounds(2).right, 1f)
        runOnIdle { width = 1400.dp }
        waitForIdle()
        assertEquals(bounds(1).top, bounds(5).top, 1f)
        assertEquals(bounds(1).height, bounds(3).height, 1f)
        assertEquals(1384f, bounds(5).right, 1f)
        runOnIdle { reference = 480.dp }
        waitForIdle()
        assertEquals(bounds(1).top, bounds(2).top, 1f)
        assertTrue(bounds(3).top > bounds(1).top)
        runOnIdle { reference = 120.dp; items = listOf(1, 2) }
        waitForIdle()
        assertTrue(bounds(2).right < 400f)
        runOnIdle { width = 100.dp }
        waitForIdle()
        assertEquals(68f, bounds(1).width, 1f)
    }

    @Test fun changingColumnsKeepsScrolledAnchorVisible() = runSkikoComposeUiTest(Size(1000f, 700f)) {
        val state = LazyGridState()
        var reference by mutableStateOf(240.dp)
        setContent { PcTheme {
            GalleryGrid((0..199).toList(), { it }, reference, Modifier.fillMaxSize(), state) { item, geometry ->
                Box(Modifier.fillMaxWidth().height(geometry.previewHeight).testTag("cell$item"))
            }
        } }
        runOnIdle { runBlocking { state.scrollToItem(80) } }
        onNodeWithTag("cell80").assertIsDisplayed()
        runOnIdle { reference = 120.dp }
        waitForIdle()
        onNodeWithTag("cell80").assertIsDisplayed()
        runOnIdle { reference = 480.dp }
        waitForIdle()
        onNodeWithTag("cell80").assertIsDisplayed()
    }
}
