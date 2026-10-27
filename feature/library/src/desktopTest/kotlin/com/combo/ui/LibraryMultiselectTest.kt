package com.combo.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.combo.Const
import com.combo.feature.library.LIB_CANCEL_TAG
import com.combo.feature.library.LIB_CREATE_COMBO_TAG
import com.combo.feature.library.LIB_DELETE_TAG
import com.combo.feature.library.LIB_SELECT_TAG
import com.combo.feature.library.LibraryScreen
import com.combo.feature.library.LibraryViewModel
import com.combo.feature.library.PHOTO_CELL_TAG_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Мультивыбор в библиотеке: вход в режим выбора, выбор нескольких фото и выход из режима.
 *
 * Правый клик включает режим выбора на desktop, обычный клик
 * по-прежнему открывает просмотр фото, а «назад» — это Esc через [pressEsc].
 */
@OptIn(ExperimentalTestApi::class)
class LibraryMultiselectTest : UiTestBase() {

    private var openedPhotoId: String? = null
    private var comboRequest: List<String>? = null

    private fun ComposeUiTest.setLibraryContent(
        ids: List<String>,
        repo: FakePhotoRepository = FakePhotoRepository(ids.map { photoEntity(it) }),
    ): LibraryViewModel {
        val vm = LibraryViewModel(repo, FakeImagePicker(), settingsRepository = com.combo.ui.FakeGallerySettingsRepository())
        val koin = trackKoin(repo)
        openedPhotoId = null
        comboRequest = null
        setContent {
            TestScreen(koin, backDispatcher) {
                LibraryScreen(
                    onOpenViewer = { id -> openedPhotoId = id },
                    onCreateCombo = { selected -> comboRequest = selected },
                    vm = vm,
                )
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + ids.first()).fetchSemanticsNodes().isNotEmpty()
        }
        return vm
    }

    private fun ComposeUiTest.cell(id: String) = onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + id)[0]

    /** Долгое нажатие в desktop-жестах — правый клик. */
    private fun ComposeUiTest.rightClickCell(id: String) {
        cell(id).performMouseInput { rightClick() }
    }

    @Test
    fun longPressSelectsPhotoAndShowsActions() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2"))
        onNodeWithText("Library").assertExists()

        rightClickCell("p2")
        waitForIdle()

        onNodeWithText("Selected: 1").assertExists()
        onNodeWithTag(LIB_CREATE_COMBO_TAG).assertExists()
        assertEquals("просмотр открываться не должен", null, openedPhotoId)
    }

    @Test
    fun selectActionEnablesModeAndTapTogglesSelection() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2"))

        onNodeWithTag(LIB_SELECT_TAG).performClick()
        waitForIdle()
        // Режим выбора без выбранных фото: заголовок уже другой, но действий ещё нет.
        onNodeWithText("Selected: 0").assertExists()
        onNodeWithTag(LIB_CREATE_COMBO_TAG).assertDoesNotExist()

        cell("p1").performClick()
        waitForIdle()
        onNodeWithText("Selected: 1").assertExists()

        cell("p2").performClick()
        waitForIdle()
        onNodeWithText("Selected: 2").assertExists()

        assertEquals("в режиме выбора тап не открывает просмотр", null, openedPhotoId)
    }

    @Test
    fun tapOutsideSelectionModeStillOpensViewer() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2"))

        cell("p1").performClick()
        waitForIdle()

        assertEquals("обычный тап обязан открыть просмотр", "p1", openedPhotoId)
        onNodeWithText("Selected: 1").assertDoesNotExist()
    }

    @Test
    fun backLeavesSelectionModeWithoutLeavingScreen() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2"))
        onNodeWithTag(LIB_SELECT_TAG).performClick()
        waitForIdle()
        cell("p1").performClick()
        waitForIdle()
        onNodeWithText("Selected: 1").assertExists()

        pressEsc()
        waitForIdle()

        onNodeWithText("Library").assertExists()
        onNodeWithTag(LIB_SELECT_TAG).assertExists()
        onNodeWithText("Selected: 1").assertDoesNotExist()
        // Выбор сброшен: следующий тап снова открывает просмотр.
        cell("p1").performClick()
        waitForIdle()
        assertEquals("p1", openedPhotoId)
    }

    @Test
    fun cancelButtonLeavesSelectionMode() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2"))
        rightClickCell("p1")
        waitForIdle()

        onNodeWithTag(LIB_CANCEL_TAG).performClick()
        waitForIdle()

        onNodeWithText("Library").assertExists()
        onNodeWithTag(LIB_CREATE_COMBO_TAG).assertDoesNotExist()
    }

    /** Режим выбора не выключается сам, когда выбор пуст: иначе снял галочку — и «потерял» режим. */
    @Test
    fun selectionModeSurvivesEmptySelection() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2"))
        rightClickCell("p1")
        waitForIdle()
        onNodeWithText("Selected: 1").assertExists()

        cell("p1").performClick()
        waitForIdle()

        onNodeWithText("Selected: 0").assertExists()
        onNodeWithTag(LIB_CANCEL_TAG).assertExists()
    }

    @Test
    fun createComboPassesSelectedIdsInSelectionOrder() = runComposeUiTest {
        setLibraryContent(listOf("p1", "p2", "p3"))
        onNodeWithTag(LIB_SELECT_TAG).performClick()
        waitForIdle()

        // Выбираем вразнобой: порядок ВЫБОРА и есть то, что уходит в билдер.
        cell("p3").performClick()
        waitForIdle()
        cell("p1").performClick()
        waitForIdle()

        onNodeWithTag(LIB_CREATE_COMBO_TAG).performClick()
        waitUntil(timeoutMillis = 5_000) { comboRequest != null }

        assertEquals(listOf("p3", "p1"), comboRequest)
    }

    /**
     * Больше [Const.MAX_ITEMS] фото в комбо не влезет, поэтому лишние отбрасываются с
     * ЯВНЫМ сообщением, а не молча. Сцена высокая: все ячейки должны быть в композиции,
     * иначе Lazy-сетка не создаст узлы для дальних фото.
     */
    @Test
    fun createComboWarnsAndTrimsAboveTheLimit() = runSkikoComposeUiTest(Size(1200f, 2600f)) {
        val ids = (1..Const.MAX_ITEMS + 3).map { "p$it" }
        setLibraryContent(ids)
        onNodeWithTag(LIB_SELECT_TAG).performClick()
        waitForIdle()
        ids.forEach { id ->
            cell(id).performClick()
            waitForIdle()
        }
        onNodeWithText("Selected: ${ids.size}").assertExists()

        onNodeWithTag(LIB_CREATE_COMBO_TAG).performClick()
        waitUntil(timeoutMillis = 5_000) { comboRequest != null }

        assertEquals(Const.MAX_ITEMS, comboRequest!!.size)
        assertEquals("передаются первые по порядку выбора", ids.take(Const.MAX_ITEMS), comboRequest)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Only the first ${Const.MAX_ITEMS} photos will be used")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun deletingSelectedClearsSelectionButKeepsScreen() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = setLibraryContent(listOf("p1", "p2"), repo)

        rightClickCell("p1")
        waitForIdle()
        cell("p2").performClick()
        waitForIdle()
        assertEquals("предусловие: выбраны оба фото", listOf("p1", "p2"), vm.selected.value)

        // performSemanticsAction, а не performClick: у IconButton в топбаре под тестом tag
        // живёт узел, координаты которого зависят от раскладки панели действий.
        onNodeWithTag(LIB_DELETE_TAG).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertTrue(
            "deleteSelected должен был сбросить выбор сразу: ${vm.selected.value}",
            vm.selected.value.isEmpty(),
        )
        waitUntil(timeoutMillis = 5_000) { repo.state.value.isEmpty() }
        assertTrue("выбранные фото должны быть удалены", repo.state.value.isEmpty())
        // По спецификации режим выбора НЕ выключается сам, когда выбор опустел, —
        // выход остаётся за «отменой» в шапке.
        onNodeWithTag(LIB_CANCEL_TAG).assertExists()
        onNodeWithTag(LIB_CANCEL_TAG).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(LIB_SELECT_TAG).assertExists()
    }
}
