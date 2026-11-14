package com.combo.feature.builder

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.domain.image.ThumbnailSource
import com.combo.ImageFixtures
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.EmptyThumbnailSource
import com.combo.ui.StubBitmapSource
import com.combo.ui.TestScreen
import com.combo.ui.UiTestBase
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Enabled-состояние кнопок тулбара, окно «+Frame» (N фото → N рамок) и замена фото.
 *
 * Инспектор показывает действия только для выбранной рамки; автоматическое вписывание
 * представлено флажком с проверяемым состоянием, а не меняющейся подписью кнопки.
 */
@OptIn(ExperimentalTestApi::class)
class ComboBuilderToolbarTest : UiTestBase() {

    private var nextId = 0

    private fun builder(
        photoIds: List<String>,
        repo: FakePhotoRepository,
        comboRepo: FakeComboRepository = FakeComboRepository(),
        comboId: String? = null,
        thumbnailSource: ThumbnailSource = EmptyThumbnailSource,
    ): ComboBuilderViewModel =
        ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("photos" to photoIds.joinToString(","), "comboId" to comboId)),
            comboRepo = comboRepo,
            photoRepo = repo,
            bitmapSource = StubBitmapSource(),
            thumbnailSource = thumbnailSource,
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = ioDispatcher,
        )

    private fun ComposeUiTest.setBuilderContent(vm: ComboBuilderViewModel) {
        setContent {
            TestScreen(backDispatcher) { ComboBuilderScreen(onBack = {}, vm = vm) }
        }
        waitUntil(timeoutMillis = 5_000) {
            vm.state.value.loaded && vm.state.value.screenW > 0f
        }
    }

    @Test
    fun existingComboWithMarginsCanBeSavedWithoutAnotherEdit() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val comboRepo = FakeComboRepository(listOf(ComboWithItems(
            comboEntity("c1"),
            listOf(comboItemEntity("large-frame", "c1").copy(framingMode = "FIT")),
        )))
        val vm = builder(emptyList(), repo, comboRepo, "c1")
        setBuilderContent(vm)
        assertFalse(vm.state.value.isDirty)
        onNodeWithTag(TOOL_TAG_SAVE).assertIsEnabled().performClick()
        waitUntil { comboRepo.savedCombos.isNotEmpty() }
        val saved = comboRepo.savedCombos.single().first
        assertEquals(1350, saved.canvasWidth)
        assertEquals(900, saved.canvasHeight)
    }

    /** Без выделения опасного действия нет в интерфейсе. */
    @Test
    fun delIsAbsentWhenTwoFramesButNoSelection() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)
        assertEquals(2, vm.state.value.items.size)
        vm.onEvent(BuilderEvent.CanvasTapped)
        waitForIdle()

        onNodeWithTag(TOOL_TAG_DEL).assertDoesNotExist()
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun delIsEnabledWithSelectionAndTwoFrames() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)
        vm.onEvent(BuilderEvent.ItemTapped(vm.state.value.items[0].id))
        waitForIdle()

        onNodeWithTag(TOOL_TAG_DEL).assertExists().assertIsEnabled()
    }

    /** Флажок доступен для выбранной рамки и отражает её сохраняемый режим вписывания. */
    @Test
    fun autoFitIsAbsentWithoutSelectionAndReflectsTheFramingMode() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)

        vm.onEvent(BuilderEvent.CanvasTapped)
        waitForIdle()
        onNodeWithTag(TOOL_TAG_AUTOFIT).assertDoesNotExist()

        vm.onEvent(BuilderEvent.ItemTapped(vm.state.value.items[0].id))
        waitForIdle()
        onNodeWithTag(TOOL_TAG_AUTOFIT).assertIsEnabled().assertIsOff().assert(hasText("Always fit"))
        assertEquals(FramingMode.FREE, vm.state.value.items[0].framingMode)

        onNodeWithTag(TOOL_TAG_AUTOFIT).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(TOOL_TAG_AUTOFIT).assertIsOn()
        assertEquals(FramingMode.FIT, vm.state.value.items[0].framingMode)
    }

    /** Единственную рамку удалить нельзя, даже выделенную. */
    @Test
    fun delIsDisabledForTheOnlyFrame() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(listOf("p1"), repo)
        setBuilderContent(vm)
        vm.onEvent(BuilderEvent.ItemTapped(vm.state.value.items.single().id))
        waitForIdle()

        onNodeWithTag(TOOL_TAG_DEL).assertExists().assertIsNotEnabled()
    }

    /** «+Frame» доступна в любом комбо и выключается ровно на лимите рамок. */
    @Test
    fun addFrameIsEnabledUntilTheItemLimit() = runComposeUiTest {
        val ids = List(Const.MAX_ITEMS) { "p$it" }
        val repo = FakePhotoRepository(ids.map { photoEntity(it) })
        val vm = builder(ids, repo)
        setBuilderContent(vm)
        assertEquals(Const.MAX_ITEMS, vm.state.value.items.size)

        onNodeWithTag(TOOL_TAG_ADD_ITEM).assertExists().assertIsNotEnabled()

        // Освобождаем место: кнопка включается.
        val lastId = vm.state.value.items.last().id
        vm.onEvent(BuilderEvent.ItemTapped(lastId))
        vm.onEvent(BuilderEvent.DeleteItem(lastId))
        waitForIdle()
        onNodeWithTag(TOOL_TAG_ADD_ITEM).assertIsEnabled()
    }

    /** Нажатие «+Frame» открывает выбор фото, а не создаёт рамку: рамок без фото не бывает. */
    @Test
    fun addFrameOpensThePickerAndDoesNotAddUntilConfirmed() = runComposeUiTest {
        val library = listOf("p1", "p2", "p3")
        val repo = FakePhotoRepository(library.map { photoEntity(it) })
        val vm = builder(listOf("p1"), repo)
        setBuilderContent(vm)
        waitUntil(timeoutMillis = 5_000) { vm.state.value.libraryPhotos.size == library.size }
        assertEquals(1, vm.state.value.items.size)

        onNodeWithTag(TOOL_TAG_ADD_ITEM).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        onNodeWithText("Add frames").assertIsDisplayed()
        assertEquals("до подтверждения рамок не прибавилось", 1, vm.state.value.items.size)
        onNodeWithTag(DIALOG_TAG_PICK_CONFIRM).assertIsNotEnabled()
    }

    /** N выбранных фото дают N рамок; последняя добавленная выделена. */
    @Test
    fun pickedPhotosBecomeFrames() = runComposeUiTest {
        val library = listOf("p1", "p2", "p3")
        val repo = FakePhotoRepository(library.map { photoEntity(it) })
        val image = ImageFixtures.patternBitmap()
        val vm = builder(listOf("p1"), repo, thumbnailSource = ThumbnailSource { image })
        setBuilderContent(vm)
        waitUntil(timeoutMillis = 5_000) { vm.state.value.libraryPhotos.size == library.size }

        onNodeWithTag(TOOL_TAG_ADD_ITEM).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { vm.pickerThumbnails.images.value.keys == library.toSet() }
        onNodeWithTag(addPhotoCellTag("p3")).performClick()
        onNodeWithTag(addPhotoCellTag("p2")).performClick()
        waitForIdle()
        onNodeWithTag(DIALOG_TAG_PICK_CONFIRM).assertIsEnabled().assert(hasText("Add 2"))
        onNodeWithTag(DIALOG_TAG_PICK_CONFIRM).performClick()
        waitForIdle()

        assertEquals(listOf("p1", "p3", "p2"), vm.state.value.items.map { it.photoId })
        assertEquals(vm.state.value.items.last().id, vm.state.value.selectedItemId)
        onNodeWithText("Add frames").assertDoesNotExist()
        assertEquals(emptyMap<String, Any>(), vm.pickerThumbnails.images.value)
    }

    /** Отметить больше свободных мест нельзя: лишнее касание игнорируется. */
    @Test
    fun pickerDoesNotAllowMoreThanTheFreeRoom() = runComposeUiTest {
        val ids = List(Const.MAX_ITEMS - 1) { "p$it" }
        // Кандидаты x1, x2 стоят первыми: сетка ленивая, и ячейки вне окна 320 dp не существуют.
        val library = listOf("x1", "x2") + ids
        val repo = FakePhotoRepository(library.map { photoEntity(it) })
        val vm = builder(ids, repo)
        setBuilderContent(vm)
        waitUntil(timeoutMillis = 5_000) { vm.state.value.libraryPhotos.size == library.size }

        onNodeWithTag(TOOL_TAG_ADD_ITEM).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(addPhotoCellTag("x1")).performClick()
        onNodeWithTag(addPhotoCellTag("x2")).performClick()
        waitForIdle()

        // Одно свободное место: окно всё равно с подтверждением, а не «коснулся — добавил».
        onNodeWithTag(DIALOG_TAG_PICK_CONFIRM).assert(hasText("Add 1"))
        assertEquals("до подтверждения рамок не прибавилось", Const.MAX_ITEMS - 1, vm.state.value.items.size)
    }

    // ---------- замена фото ----------

    @Test
    fun replaceIsAbsentWithoutSelection() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = builder(listOf("p1", "p2"), repo)
        setBuilderContent(vm)

        vm.onEvent(BuilderEvent.CanvasTapped)
        waitForIdle()
        onNodeWithTag(TOOL_TAG_REPLACE_PHOTO).assertDoesNotExist()

        vm.onEvent(BuilderEvent.ItemTapped(vm.state.value.items[0].id))
        waitForIdle()
        onNodeWithTag(TOOL_TAG_REPLACE_PHOTO).assertIsEnabled()
    }

    /** Касание фото в окне замены сразу заменяет и закрывает окно; текущее фото не предлагается. */
    @Test
    fun replaceSwapsThePhotoKeepsTheFrameAndClosesTheDialog() = runComposeUiTest {
        val library = listOf("p1", "p2", "p3")
        val repo = FakePhotoRepository(library.map { photoEntity(it) })
        val vm = builder(listOf("p1"), repo)
        setBuilderContent(vm)
        waitUntil(timeoutMillis = 5_000) { vm.state.value.libraryPhotos.size == library.size }
        val before = vm.state.value.items.single()
        vm.onEvent(BuilderEvent.ItemTapped(before.id))
        waitForIdle()

        onNodeWithTag(TOOL_TAG_REPLACE_PHOTO).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Replace photo").assertIsDisplayed()
        onNodeWithTag(addPhotoCellTag("p1")).assertDoesNotExist()

        onNodeWithTag(addPhotoCellTag("p2")).performClick()
        waitForIdle()

        val after = vm.state.value.items.single()
        assertEquals("p2", after.photoId)
        assertEquals("рамка та же", before.id, after.id)
        assertEquals(before.centerX, after.centerX, 0f)
        assertEquals(before.width, after.width, 0f)
        onNodeWithText("Replace photo").assertDoesNotExist()
    }
}
