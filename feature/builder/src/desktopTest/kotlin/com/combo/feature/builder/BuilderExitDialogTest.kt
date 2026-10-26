package com.combo.feature.builder

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.ComboWithItems
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.TestScreen
import com.combo.ui.UiTestBase
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import com.combo.ui.pressEsc
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Выход из редактора с подтверждением, если есть несохранённые правки.
 *
 * Проверяются и кнопки диалога, и системный «назад»: стрелка в топбаре и Esc должны
 * вести себя одинаково и не терять правки молча.
 */
@OptIn(ExperimentalTestApi::class)
class BuilderExitDialogTest : UiTestBase() {

    private var backCalls = 0
    private var nextId = 0

    private fun builder(
        repo: FakePhotoRepository,
        comboRepo: FakeComboRepository = FakeComboRepository(),
        state: Map<String, Any> = emptyMap(),
    ): ComboBuilderViewModel = ComboBuilderViewModel(
        savedStateHandle = SavedStateHandle(state),
        comboRepo = comboRepo,
        photoRepo = repo,
        bitmapSource = StubBitmapSource(),
        ids = IdGenerator { "gen-${nextId++}" },
        clock = WallClock { 1_000L },
        ioDispatcher = ioDispatcher,
    )

    private fun ComposeUiTest.setBuilderContent(vm: ComboBuilderViewModel, repo: FakePhotoRepository) {
        backCalls = 0
        val koin = trackKoin(repo)
        setContent {
            TestScreen(koin, backDispatcher) { ComboBuilderScreen(onBack = { backCalls++ }, vm = vm) }
        }
        waitUntil(timeoutMillis = 5_000) {
            vm.state.value.loaded && vm.state.value.screenW > 0f
        }
    }

    @Test
    fun cleanEditorExitsImmediatelyWithoutDialog() = runComposeUiTest {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Trip"),
                    items = listOf(comboItemEntity("i1", "c1", 0, photoId = "p1")),
                ),
            ),
        )
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, comboRepo, mapOf("comboId" to "c1"))
        setBuilderContent(vm, repo)
        assertFalse("предусловие: комбо не изменено", vm.state.value.isDirty)

        pressEsc()
        waitForIdle()

        assertEquals("чистый редактор должен выйти сразу, без диалога", 1, backCalls)
        onNodeWithTag(DIALOG_TAG_SAVE).assertDoesNotExist()
    }

    @Test
    fun dirtyEditorAsksAndDiscardExits() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, state = mapOf("photos" to "p1"))
        setBuilderContent(vm, repo)
        assertTrue("предусловие: New combo сразу считается изменённым", vm.state.value.isDirty)

        pressEsc()
        waitForIdle()

        onNodeWithTag(DIALOG_TAG_KEEP).assertExists()
        assertEquals("диалог показан — выхода ещё не было", 0, backCalls)

        onNodeWithTag(DIALOG_TAG_DISCARD).performClick()
        waitForIdle()
        assertEquals("Discard должен выйти без сохранения", 1, backCalls)
    }

    @Test
    fun keepEditingClosesDialogAndStaysInEditor() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, state = mapOf("photos" to "p1"))
        setBuilderContent(vm, repo)

        pressEsc()
        waitForIdle()
        onNodeWithTag(DIALOG_TAG_KEEP).performClick()
        waitForIdle()

        assertEquals("Keep editing не выходит", 0, backCalls)
        onNodeWithTag(DIALOG_TAG_KEEP).assertDoesNotExist()
        onNodeWithTag(EDITOR_CANVAS_TAG).assertExists()
    }

    @Test
    fun saveInDialogSavesAndExits() = runComposeUiTest {
        val comboRepo = FakeComboRepository()
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, comboRepo, mapOf("photos" to "p1"))
        setBuilderContent(vm, repo)

        pressEsc()
        waitForIdle()
        onNodeWithTag(DIALOG_TAG_SAVE).performClick()

        // Выход происходит по сигналу `saved` из корутины сохранения.
        waitUntil(timeoutMillis = 5_000) { backCalls > 0 }
        assertEquals("Save должен сохранить и выйти", 1, backCalls)
        assertTrue("комбо должно было сохраниться", comboRepo.savedCombos.isNotEmpty())
    }

    @Test
    fun saveWithoutPhotosShowsMessageAndKeepsEditorOpen() = runComposeUiTest {
        val repo = FakePhotoRepository()
        val vm = builder(repo)
        setBuilderContent(vm, repo)
        // New combo без фото: рамок нет, сохранять нечего.
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue("предусловие: комбо помечено изменённым", vm.state.value.isDirty)

        pressEsc()
        waitForIdle()
        onNodeWithTag(DIALOG_TAG_SAVE).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Add at least one photo").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("редактор должен остаться открытым", 0, backCalls)
        onNodeWithTag(EDITOR_CANVAS_TAG).assertExists()
    }

    @Test
    fun topBarBackArrowUsesTheSameConfirmation() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, state = mapOf("photos" to "p1"))
        setBuilderContent(vm, repo)

        onNodeWithContentDescription("Back").performClick()
        waitForIdle()

        onNodeWithTag(DIALOG_TAG_DISCARD).assertExists()
        assertEquals("стрелка тоже обязана спрашивать", 0, backCalls)
    }

    @Test
    fun toolbarSaveWithoutPhotosAlsoReportsIt() = runComposeUiTest {
        val repo = FakePhotoRepository()
        val vm = builder(repo)
        setBuilderContent(vm, repo)

        onNodeWithTag(TOOL_TAG_SAVE).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Add at least one photo").fetchSemanticsNodes().isNotEmpty()
        }

        assertFalse("сохранения не должно было произойти", vm.state.value.saving)
    }

    /**
     * Пока идёт сохранение, диалог (если его успели открыть) не должен предлагать
     * действия. Состояние делаем наблюдаемым через гейт в фейковом репозитории —
     * иначе `saving` живёт миллисекунды и поймать его нельзя.
     */
    @Test
    fun dialogButtonsAreDisabledWhileSaving() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val comboRepo = FakeComboRepository().apply { saveGate = { gate.await() } }
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, comboRepo, mapOf("photos" to "p1"))
        setBuilderContent(vm, repo)

        onNodeWithTag(TOOL_TAG_SAVE).performClick()
        waitUntil(timeoutMillis = 5_000) { vm.state.value.saving }

        pressEsc()
        waitForIdle()

        onNodeWithTag(DIALOG_TAG_SAVE).assertIsNotEnabled()
        onNodeWithTag(DIALOG_TAG_DISCARD).assertIsNotEnabled()
        onNodeWithTag(DIALOG_TAG_KEEP).assertIsNotEnabled()

        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { backCalls > 0 }
    }

    @Test
    fun dialogButtonsAreEnabledInNormalState() = runComposeUiTest {
        val repo = FakePhotoRepository(listOf(photoEntity("p1")))
        val vm = builder(repo, state = mapOf("photos" to "p1"))
        setBuilderContent(vm, repo)
        pressEsc()
        waitForIdle()

        onNodeWithTag(DIALOG_TAG_SAVE).assertIsEnabled()
        onNodeWithTag(DIALOG_TAG_DISCARD).assertIsEnabled()
        onNodeWithTag(DIALOG_TAG_KEEP).assertIsEnabled()
    }
}
