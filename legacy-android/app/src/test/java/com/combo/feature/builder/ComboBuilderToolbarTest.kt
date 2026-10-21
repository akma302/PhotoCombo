package com.combo.feature.builder

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.photoEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B6: enabled-состояние кнопок тулбара и окно диалога +Photo. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComboBuilderToolbarTest {

    @get:Rule
    val compose = createComposeRule()

    private fun vm(photoIds: List<String>): ComboBuilderViewModel =
        ComboBuilderViewModel(
            SavedStateHandle(mapOf("photos" to photoIds.joinToString(","))),
            FakeComboRepository(),
            FakePhotoRepository(photoIds.map { photoEntity(it) }),
            StubBitmapSource(),
        )

    private fun setContent(vm: ComboBuilderViewModel) {
        compose.setContent { ComboBuilderScreen(onBack = {}, vm = vm) }
        compose.waitUntil(timeoutMillis = 5_000) {
            vm.state.value.loaded && vm.state.value.screenW > 0f
        }
    }

    /** Две рамки, но выделения нет — Del обязана быть выключена (иначе был NPE на `sel!!`). */
    @Test
    fun delIsDisabledWhenTwoFramesButNoSelection() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)
        assertEquals(2, vm.state.value.items.size)
        vm.onEvent(BuilderEvent.CanvasTapped)
        compose.waitForIdle()

        // Del завязана на выделение рамки, поэтому здесь важно именно enabled кнопки:
        // клик по выключенной кнопке ничего не делает и ничего не проверяет.
        // Заодно Del находится в горизонтально прокручиваемом тулбаре и может быть за
        // краем экрана, поэтому проверяем наличие и enabled, а не видимость.
        compose.onNodeWithTag(TOOL_TAG_DEL).assertExists().assertIsNotEnabled()
    }

    @Test
    fun delIsEnabledWithSelectionAndTwoFrames() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)
        vm.onEvent(BuilderEvent.ItemTapped(vm.state.value.items[0].id))
        compose.waitForIdle()

        compose.onNodeWithTag(TOOL_TAG_DEL).assertExists().assertIsEnabled()
    }

    /** Кнопка AutoFit включается только с выделенной рамкой и меняет подпись по режиму. */
    @Test
    fun autoFitIsDisabledWithoutSelectionAndFlipsItsLabelOnClick() {
        val vm = vm(listOf("p1", "p2"))
        setContent(vm)

        vm.onEvent(BuilderEvent.CanvasTapped)
        compose.waitForIdle()
        compose.onNodeWithTag(TOOL_TAG_AUTOFIT).assertExists().assertIsNotEnabled()

        vm.onEvent(BuilderEvent.ItemTapped(vm.state.value.items[0].id))
        compose.waitForIdle()
        compose.onNodeWithTag(TOOL_TAG_AUTOFIT).assertIsEnabled().assert(hasText("AutoFit OFF"))
        assertEquals(FramingMode.FREE, vm.state.value.items[0].framingMode)

        compose.onNodeWithTag(TOOL_TAG_AUTOFIT).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TOOL_TAG_AUTOFIT).assert(hasText("AutoFit ON"))
        assertEquals(FramingMode.FIT, vm.state.value.items[0].framingMode)
    }

    /** Диалог закрывается ровно тогда, когда в рамке стало MAX_CONTENTS_PER_ITEM фото. */
    @Test
    fun addPhotoDialogClosesExactlyWhenFrameIsFull() {
        // Одна рамка с одним фото + в библиотеке ещё MAX-1 кандидатов: New combo делает
        // по рамке на фото, поэтому «рамка на 5 фото» набирается только через диалог.
        val library = (1..Const.MAX_CONTENTS_PER_ITEM).map { "p$it" }
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("photos" to "p1")),
            FakeComboRepository(),
            FakePhotoRepository(library.map { photoEntity(it) }),
            StubBitmapSource(),
        )
        setContent(vm)
        assertEquals(1, vm.state.value.items.size)
        assertEquals(1, vm.state.value.items.single().contents.size)

        compose.onNodeWithTag(TOOL_TAG_ADD_PHOTO).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add photos to item").assertIsDisplayed()

        val candidates = library.drop(1)
        assertEquals("столько фото помещается в рамку", Const.MAX_CONTENTS_PER_ITEM - 1, candidates.size)

        // До лимита окно остаётся открытым.
        candidates.take(Const.MAX_CONTENTS_PER_ITEM - 2).forEachIndexed { i, id ->
            compose.onNodeWithTag(addPhotoCellTag(id)).performClick()
            compose.waitForIdle()
            assertEquals("после ${i + 1} добавлений", i + 2, vm.state.value.items.single().contents.size)
            compose.onNodeWithText("Add photos to item").assertIsDisplayed()
        }

        // Последнее фото добивает лимит — окно закрывается само.
        compose.onNodeWithTag(addPhotoCellTag(candidates.last())).performClick()
        compose.waitForIdle()
        assertEquals(Const.MAX_CONTENTS_PER_ITEM, vm.state.value.items.single().contents.size)
        compose.onNodeWithText("Add photos to item").assertDoesNotExist()
    }
}
