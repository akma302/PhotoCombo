package com.combo.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.Const
import com.combo.MainActivity
import com.combo.feature.library.LIB_CANCEL_TAG
import com.combo.feature.library.LIB_CREATE_COMBO_TAG
import com.combo.feature.library.LIB_DELETE_TAG
import com.combo.feature.library.LIB_SELECT_TAG
import com.combo.feature.library.LibraryScreen
import com.combo.feature.library.LibraryViewModel
import com.combo.feature.library.PHOTO_CELL_TAG_PREFIX
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Мультивыбор в библиотеке: вход в режим выбора, выбор нескольких фото и выход из режима.
 *
 * Войти в режим можно долгим нажатием на фото или пунктом «Select». Обычный тап при этом
 * по-прежнему открывает просмотр фото.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryMultiselectTest {

    @get:Rule
    val compose = createComposeRule()
    //    @get:Rule
    //    val compose = createAndroidComposeRule<MainActivity>()

    private var openedPhotoId: String? = null
    private var comboRequest: List<String>? = null

    private fun setContent(ids: List<String>, vm: LibraryViewModel = LibraryViewModel(FakePhotoRepository(ids.map { photoEntity(it) }))) {
        openedPhotoId = null
        comboRequest = null
        compose.setContent {
            LibraryScreen(
                onOpenViewer = { id -> openedPhotoId = id },
                onCreateCombo = { ids -> comboRequest = ids },
                vm = vm,
            )
        }
        val first = compose.onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + ids.first())
        awaitUntil { first.fetchSemanticsNodes().isNotEmpty() }
    }

    private fun cell(id: String) = compose.onAllNodesWithTag(PHOTO_CELL_TAG_PREFIX + id)[0]

    /**
     * `viewModelScope` идёт по диспетчеру Robolectric, а `compose.waitUntil` его не
     * прокручивает — нужен явный прогон главного лупера (как в `PlayerAndCacheTest`).
     */
    private fun awaitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            compose.waitForIdle()
        }
        assertTrue("условие не выполнилось за ${timeoutMs}мс", cond())
    }

    @Test
    fun longPressSelectsPhotoAndShowsActions() {
        setContent(listOf("p1", "p2"))
        compose.onNodeWithText("Library").assertExists()

        cell("p2").performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Selected: 1").assertExists()
        compose.onNodeWithTag(LIB_CREATE_COMBO_TAG).assertExists()
        assertEquals("просмотр открываться не должен", null, openedPhotoId)
    }

    @Test
    fun selectActionEnablesModeAndTapTogglesSelection() {
        setContent(listOf("p1", "p2"))

        compose.onNodeWithTag(LIB_SELECT_TAG).performClick()
        compose.waitForIdle()
        // Режим выбора без выбранных фото: заголовок уже другой, но действий ещё нет.
        compose.onNodeWithText("Selected: 0").assertExists()
        compose.onNodeWithTag(LIB_CREATE_COMBO_TAG).assertDoesNotExist()

        cell("p1").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Selected: 1").assertExists()

        cell("p2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Selected: 2").assertExists()

        assertEquals("в режиме выбора тап не открывает просмотр", null, openedPhotoId)
    }

    @Test
    fun tapOutsideSelectionModeStillOpensViewer() {
        setContent(listOf("p1", "p2"))

        cell("p1").performClick()
        compose.waitForIdle()

        assertEquals("обычный тап обязан открыть просмотр", "p1", openedPhotoId)
        compose.onNodeWithText("Selected: 1").assertDoesNotExist()
    }

    @Test
    fun backLeavesSelectionModeWithoutLeavingScreen() {
        setContent(listOf("p1", "p2"))
        compose.onNodeWithTag(LIB_SELECT_TAG).performClick()
        compose.waitForIdle()
        cell("p1").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Selected: 1").assertExists()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText("Library").assertExists()
        compose.onNodeWithTag(LIB_SELECT_TAG).assertExists()
        compose.onNodeWithText("Selected: 1").assertDoesNotExist()
        // Выбор сброшен: следующий тап снова открывает просмотр.
        cell("p1").performClick()
        compose.waitForIdle()
        assertEquals("p1", openedPhotoId)
    }

    @Test
    fun cancelButtonLeavesSelectionMode() {
        setContent(listOf("p1", "p2"))
        cell("p1").performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithTag(LIB_CANCEL_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Library").assertExists()
        compose.onNodeWithTag(LIB_CREATE_COMBO_TAG).assertDoesNotExist()
    }

    /** Режим выбора не выключается сам, когда выбор пуст: иначе снял галочку — и «потерял» режим. */
    @Test
    fun selectionModeSurvivesEmptySelection() {
        setContent(listOf("p1", "p2"))
        cell("p1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Selected: 1").assertExists()

        cell("p1").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Selected: 0").assertExists()
        compose.onNodeWithTag(LIB_CANCEL_TAG).assertExists()
    }

    @Test
    fun createComboPassesSelectedIdsInSelectionOrder() {
        setContent(listOf("p1", "p2", "p3"))
        compose.onNodeWithTag(LIB_SELECT_TAG).performClick()
        compose.waitForIdle()

        // Выбираем вразнобой: порядок ВЫБОРА и есть то, что уходит в билдер.
        cell("p3").performClick()
        compose.waitForIdle()
        cell("p1").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(LIB_CREATE_COMBO_TAG).performClick()
        awaitUntil { comboRequest != null }

        assertEquals(listOf("p3", "p1"), comboRequest)
    }

    /**
     * Больше [Const.MAX_ITEMS] фото в комбо не влезет, поэтому лишние отбрасываются с
     * ЯВНЫМ сообщением, а не молча.
     */
    @Test
    fun createComboWarnsAndTrimsAboveTheLimit() {
        val ids = (1..Const.MAX_ITEMS + 3).map { "p$it" }
        setContent(ids)
        compose.onNodeWithTag(LIB_SELECT_TAG).performClick()
        compose.waitForIdle()
        ids.forEach { id ->
            cell(id).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Selected: ${ids.size}").assertExists()

        compose.onNodeWithTag(LIB_CREATE_COMBO_TAG).performClick()
        awaitUntil { comboRequest != null }

        assertEquals(Const.MAX_ITEMS, comboRequest!!.size)
        assertEquals("передаются первые по порядку выбора", ids.take(Const.MAX_ITEMS), comboRequest)
        compose.waitForIdle()
        compose.onNodeWithText("Only the first ${Const.MAX_ITEMS} photos will be used").assertExists()
    }

    @Test
    fun deletingSelectedClearsSelectionButKeepsScreen() {
        val repo = FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2")))
        val vm = LibraryViewModel(repo)
        setContent(listOf("p1", "p2"), vm)

        cell("p1").performTouchInput { longClick() }
        compose.waitForIdle()
        cell("p2").performClick()
        compose.waitForIdle()
        assertEquals("предусловие: выбраны оба фото", listOf("p1", "p2"), vm.selected.value)

        // performSemanticsAction, а не performClick: у IconButton в топбаре под тестом tag
        // живёт узел, координаты которого зависят от раскладки панели действий.
        compose.onNodeWithTag(LIB_DELETE_TAG).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(
            "deleteSelected должен был сбросить выбор сразу: ${vm.selected.value}",
            vm.selected.value.isEmpty(),
        )
        awaitUntil { repo.state.value.isEmpty() }
        assertTrue("выбранные фото должны быть удалены", repo.state.value.isEmpty())
        // По спецификации режим выбора НЕ выключается сам, когда выбор опустел, —
        // выход остаётся за «отменой» в шапке.
        compose.onNodeWithTag(LIB_CANCEL_TAG).assertExists()
        compose.onNodeWithTag(LIB_CANCEL_TAG).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(LIB_SELECT_TAG).assertExists()
    }
}