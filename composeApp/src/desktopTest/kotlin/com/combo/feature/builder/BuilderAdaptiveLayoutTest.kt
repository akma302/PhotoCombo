package com.combo.feature.builder

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ImageFixtures
import com.combo.domain.BitmapSource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.combo.core.background.BackgroundSpec
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.feature.builder.draft.toSnap
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.TestScreen
import com.combo.ui.UiTestBase
import com.combo.ui.photoEntity
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Реальное изменение ширины одной сцены: геометрия, диалоги и управление холстом. */
@OptIn(ExperimentalTestApi::class)
class BuilderAdaptiveLayoutTest : UiTestBase() {
    private val repo = FakePhotoRepository((1..3).map { photoEntity("p$it") })
    private var nextId = 0
    private var width by mutableStateOf(1200.dp)
    private var height by mutableStateOf(760.dp)
    private var dark by mutableStateOf(false)
    private var sceneDensity by mutableStateOf(Density(1f))

    private fun builder() = ComboBuilderViewModel(
        savedStateHandle = SavedStateHandle(mapOf("photos" to "p1,p2")),
        comboRepo = FakeComboRepository(), photoRepo = repo, bitmapSource = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap = ImageFixtures.halvesBitmap()
        },
        ids = IdGenerator { "frame-${nextId++}" }, clock = WallClock { 1_000L }, ioDispatcher = ioDispatcher,
    )

    private fun ComposeUiTest.show(vm: ComboBuilderViewModel) {
        val koin = trackKoin(repo)
        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalDensity provides sceneDensity) {
                    Surface(Modifier.width(width).height(height).testTag("adaptive_builder_root")) {
                        PcDesktopTheme(motion = PcMotion(reducedMotion = true)) {
                            TestScreen(koin, backDispatcher) { ComboBuilderScreen(onBack = {}, vm = vm) }
                        }
                    }
                }
            }
        }
        waitUntil { vm.state.value.loaded && vm.state.value.screenW > 0 }
    }

    private fun ComposeUiTest.resize(value: Int) {
        runOnIdle { width = value.dp }
        waitForIdle()
    }

    private fun ComposeUiTest.capture(name: String) {
        val bitmap = onNodeWithTag("adaptive_builder_root").captureToImage()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
        }
        File("build/ui-previews/$name.png").apply { parentFile.mkdirs(); writeBytes(bytes) }
    }

    @Test
    fun controlsStayVisibleAtAllWidthsAndTheInspectorMovesBesideTheCanvas() = runSkikoComposeUiTest(Size(1200f, 800f), density = Density(1f)) {
        val vm = builder()
        show(vm)
        for (theme in listOf(false, true)) {
            runOnIdle { dark = theme }
            for (windowWidth in listOf(420, 800, 1200)) {
                runOnIdle { height = if (windowWidth < 760) 700.dp else 760.dp }
                resize(windowWidth)
                val canvas = onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
                val panel = onNodeWithTag(BUILDER_INSPECTOR_TAG).fetchSemanticsNode().boundsInRoot
                val root = onNodeWithTag("adaptive_builder_root").fetchSemanticsNode().boundsInRoot
                assertTrue("холст сохраняет рабочую высоту", canvas.height >= 200)
                if (windowWidth >= 760) {
                    assertTrue("инспектор справа", panel.left >= canvas.right - 1)
                } else {
                    assertTrue("инспектор снизу", panel.top >= canvas.bottom - 1)
                }
                listOf(TOOL_TAG_SAVE, TOOL_TAG_ADD_ITEM, TOOL_TAG_ASPECT, TOOL_TAG_BACKGROUND,
                    TOOL_TAG_MODE_FRAME, TOOL_TAG_MODE_PHOTO, TOOL_TAG_FIT, TOOL_TAG_FILL,
                    TOOL_TAG_AUTOFIT, TOOL_TAG_REPLACE_PHOTO, TOOL_TAG_DEL, TOOL_TAG_FIT_VIEW,
                ).forEach { tag ->
                    onNodeWithTag(tag).assertIsDisplayed()
                    val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    assertTrue("$tag внутри окна $windowWidth", bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom)
                }
                capture("builder-$windowWidth-${if (theme) "dark" else "light"}")
            }
        }
    }

    @Test
    fun darkBuilderRendersAcrossDensityAndTextScale() = runSkikoComposeUiTest(Size(1800f, 1140f)) {
        val vm = builder()
        show(vm)
        for (density in listOf(1f, 1.5f)) {
            for (font in listOf(1f, 1.25f)) {
                for (windowWidth in listOf(420, 800, 1200)) {
                    runOnIdle {
                        sceneDensity = Density(density, font)
                        width = windowWidth.dp
                        height = if (windowWidth == 1200) 760.dp else 700.dp
                    }
                    waitForIdle()
                    onNodeWithTag(TOOL_TAG_SAVE).assertIsDisplayed()
                    onNodeWithTag(TOOL_TAG_ADD_ITEM).assertIsDisplayed()
                    onNodeWithTag(TOOL_TAG_FIT_VIEW).assertIsDisplayed()
                    capture("builder-ds-$windowWidth-$density-$font")
                }
            }
        }
    }

    @Test
    fun collapsingInspectorChangesOnlyAvailableSpace() = runSkikoComposeUiTest(Size(1200f, 800f)) {
        val vm = builder()
        show(vm)
        val items = vm.state.value.items.map { it.toSnap() }
        val mode = vm.state.value.mode
        val widthBefore = onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode().boundsInRoot.width
        onNodeWithContentDescription("Hide inspector").performClick()
        waitForIdle()
        assertTrue(onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode().boundsInRoot.width > widthBefore)
        assertEquals(items, vm.state.value.items.map { it.toSnap() })
        assertEquals(mode, vm.state.value.mode)
        onNodeWithContentDescription("Show inspector").performClick()
        waitForIdle()
        assertEquals(widthBefore, onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun resizingPreservesDraftHistoryModeViewAndPendingPhotoSelection() = runSkikoComposeUiTest(Size(1200f, 800f), density = Density(1f)) {
        val vm = builder()
        show(vm)
        onNodeWithTag(TOOL_TAG_MODE_PHOTO).performClick()
        onNodeWithTag(TOOL_TAG_ZOOM_IN).performClick()
        onNodeWithTag(TOOL_TAG_FIT).performClick()
        val before = vm.state.value
        val itemsBefore = before.items.map { it.toSnap() }
        assertTrue(before.canUndo)

        onNodeWithTag(TOOL_TAG_ADD_ITEM).performClick()
        onNodeWithTag(addPhotoCellTag("p3")).performClick()
        resize(420)
        onNodeWithText("Add 1").assertIsDisplayed()
        assertEquals(itemsBefore, vm.state.value.items.map { it.toSnap() })
        assertEquals(before.selectedItemId, vm.state.value.selectedItemId)
        assertEquals(before.viewport, vm.state.value.viewport)
        assertEquals(before.mode, vm.state.value.mode)
        assertEquals(before.canUndo, vm.state.value.canUndo)
        onNodeWithTag(DIALOG_TAG_PICK_CONFIRM).performClick()
        waitForIdle()
        assertEquals(listOf("p1", "p2", "p3"), vm.state.value.items.map { it.photoId })

        onNodeWithTag(TOOL_TAG_REPLACE_PHOTO).performClick()
        resize(1200)
        onNodeWithText("Replace photo").assertIsDisplayed()
        onNodeWithTag(addPhotoCellTag("p1")).performClick()
        waitForIdle()
        assertEquals("p1", vm.state.value.items.last().photoId)
    }

    @Test
    fun zoomControlsChangeOnlyTheViewAndResetIsDisabledAtTheInitialPosition() = runSkikoComposeUiTest(Size(1200f, 800f), density = Density(1f)) {
        val vm = builder()
        show(vm)
        resize(420)
        val items = vm.state.value.items.map { it.toSnap() }
        val dirty = vm.state.value.isDirty
        onNodeWithTag(TOOL_TAG_FIT_VIEW).assertIsNotEnabled()

        onNodeWithTag(TOOL_TAG_ZOOM_IN).performClick()
        waitForIdle()
        onNodeWithText("120%").assertIsDisplayed()
        onNodeWithTag(TOOL_TAG_FIT_VIEW).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithText("100%").assertIsDisplayed()
        onNodeWithTag(TOOL_TAG_FIT_VIEW).assertIsNotEnabled()
        assertEquals(items, vm.state.value.items.map { it.toSnap() })
        assertEquals(dirty, vm.state.value.isDirty)
        assertTrue(!vm.state.value.canUndo)
    }

    @Test
    fun formatAndBackgroundMenusApplyTheChosenValues() = runSkikoComposeUiTest(Size(1200f, 800f), density = Density(1f)) {
        val vm = builder()
        show(vm)
        resize(420)
        onNodeWithTag(TOOL_TAG_ASPECT).performClick()
        onNodeWithText("1:1").performClick()
        waitForIdle()
        assertEquals(1200f, vm.state.value.canvasW, 0f)
        assertEquals(1200f, vm.state.value.canvasH, 0f)
        onNodeWithTag(TOOL_TAG_BACKGROUND).performClick()
        onNodeWithText("Black").performClick()
        waitForIdle()
        assertEquals(BackgroundSpec.Solid(0xFF000000.toInt()), vm.state.value.background)
    }

    @Test
    fun frameDraggingStillUsesTheCanvasCoordinatesAfterResizing() = runSkikoComposeUiTest(Size(1200f, 800f), density = Density(1f)) {
        val vm = builder()
        show(vm)
        resize(420)
        val state = vm.state.value
        val item = state.items[1]
        val oldX = item.centerX
        val oldY = item.centerY
        val scale = vm.currentVs()
        val origin = Offset((state.screenW - state.canvasW * scale) / 2f, (state.screenH - state.canvasH * scale) / 2f)
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(origin + Offset(oldX * scale, oldY * scale))
            press()
            moveBy(Offset(-25f, 15f))
            moveBy(Offset(-25f, 15f))
            release()
        }
        mainClock.advanceTimeBy(400)
        waitForIdle()
        assertEquals(item.id, vm.state.value.selectedItemId)
        assertTrue(vm.state.value.items[1].centerX < oldX)
        assertTrue(vm.state.value.items[1].centerY > oldY)
        assertTrue(vm.state.value.canUndo)
    }
}
