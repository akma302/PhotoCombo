package com.combo.feature.builder

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import com.combo.ui.comboContentEntity
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Выход из редактора с подтверждением, если есть несохранённые правки.
 *
 * Проверяются и кнопки диалога, и СИСТЕМНАЯ кнопка «назад»: стрелка в топбаре и «назад»
 * должны вести себя одинаково и не терять правки молча.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuilderExitDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private var backCalls = 0

    /** Открытый и загруженный комбо без правок — редактор «чистый». */
    private fun loadedCombo(comboRepo: FakeComboRepository = FakeComboRepository()) = ComboBuilderViewModel(
        SavedStateHandle(mapOf("comboId" to "c1")),
        comboRepo,
        FakePhotoRepository(listOf(photoEntity("p1"))),
        StubBitmapSource(),
    )

    private fun emptyCombo() = ComboBuilderViewModel(
        SavedStateHandle(emptyMap()),
        FakeComboRepository(),
        FakePhotoRepository(emptyList()),
        StubBitmapSource(),
    )

    /** New combo из одного фото — сразу считается изменённым (isDirty = true). */
    private fun newCombo(photoIds: List<String>, comboRepo: FakeComboRepository) = ComboBuilderViewModel(
        SavedStateHandle(mapOf("photos" to photoIds.joinToString(","))),
        comboRepo,
        FakePhotoRepository(photoIds.map { photoEntity(it) }),
        StubBitmapSource(),
    )

    private fun setContent(vm: ComboBuilderViewModel) {
        backCalls = 0
        compose.setContent { ComboBuilderScreen(onBack = { backCalls++ }, vm = vm) }
        compose.waitUntil(timeoutMillis = 5_000) {
            vm.state.value.loaded && vm.state.value.screenW > 0f
        }
    }

    private fun awaitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            compose.waitForIdle()
            Thread.sleep(5)
        }
    }

    @Test
    fun cleanEditorExitsImmediatelyWithoutDialog() {
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Trip"),
                    items = listOf(
                        ItemWithContents(
                            comboItemEntity("i1", "c1", 0),
                            listOf(comboContentEntity("ct1", "i1", "p1")),
                        ),
                    ),
                ),
            ),
        )
        val vm = loadedCombo(comboRepo)
        setContent(vm)
        assertFalse("предусловие: комбо не изменено", vm.state.value.isDirty)

        Espresso.pressBack()
        compose.waitForIdle()

        assertEquals("чистый редактор должен выйти сразу, без диалога", 1, backCalls)
        compose.onNodeWithTag(DIALOG_TAG_SAVE).assertDoesNotExist()
    }

    @Test
    fun dirtyEditorAsksAndDiscardExits() {
        val vm = newCombo(listOf("p1"), FakeComboRepository())
        setContent(vm)
        assertTrue("предусловие: New combo сразу считается изменённым", vm.state.value.isDirty)

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag(DIALOG_TAG_KEEP).assertExists()
        assertEquals("диалог показан — выхода ещё не было", 0, backCalls)

        compose.onNodeWithTag(DIALOG_TAG_DISCARD).performClick()
        compose.waitForIdle()
        assertEquals("Discard должен выйти без сохранения", 1, backCalls)
    }

    @Test
    fun keepEditingClosesDialogAndStaysInEditor() {
        val vm = newCombo(listOf("p1"), FakeComboRepository())
        setContent(vm)

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(DIALOG_TAG_KEEP).performClick()
        compose.waitForIdle()

        assertEquals("Keep editing не выходит", 0, backCalls)
        compose.onNodeWithTag(DIALOG_TAG_KEEP).assertDoesNotExist()
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).assertExists()
    }

    @Test
    fun saveInDialogSavesAndExits() {
        val comboRepo = FakeComboRepository()
        val vm = newCombo(listOf("p1"), comboRepo)
        setContent(vm)

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(DIALOG_TAG_SAVE).performClick()

        // Выход происходит по сигналу `saved`, он приходит из корутины на IO.
        awaitUntil { backCalls > 0 }
        compose.waitForIdle()
        assertEquals("Save должен сохранить и выйти", 1, backCalls)
        assertTrue("комбо должно было сохраниться", comboRepo.savedCombos.isNotEmpty())
    }

    @Test
    fun saveWithoutPhotosShowsMessageAndKeepsEditorOpen() {
        val vm = emptyCombo()
        setContent(vm)
        // New combo без фото: рамка есть, сохранять нечего.
        assertTrue(vm.state.value.items.none { it.contents.isNotEmpty() })
        assertTrue("предусловие: комбо помечено изменённым", vm.state.value.isDirty)

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(DIALOG_TAG_SAVE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Add at least one photo").assertExists()
        assertEquals("редактор должен остаться открытым", 0, backCalls)
        compose.onNodeWithTag(EDITOR_CANVAS_TAG).assertExists()
    }

    @Test
    fun topBarBackArrowUsesTheSameConfirmation() {
        val vm = newCombo(listOf("p1"), FakeComboRepository())
        setContent(vm)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(DIALOG_TAG_DISCARD).assertExists()
        assertEquals("стрелка тоже обязана спрашивать", 0, backCalls)
    }

    @Test
    fun toolbarSaveWithoutPhotosAlsoReportsIt() {
        val vm = emptyCombo()
        setContent(vm)

        compose.onNodeWithTag(TOOL_TAG_SAVE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Add at least one photo").assertExists()
        assertFalse("сохранения не должно было произойти", vm.state.value.saving)
    }

    /**
     * Пока идёт сохранение, диалог (если его успели открыть) не должен предлагать
     * действия. Состояние делаем наблюдаемым через гейт в фейковом репозитории —
     * иначе `saving` живёт миллисекунды и поймать его нельзя.
     */
    @Test
    fun dialogButtonsAreDisabledWhileSaving() {
        val gate = CompletableDeferred<Unit>()
        val comboRepo = FakeComboRepository().apply { saveGate = { gate.await() } }
        val vm = newCombo(listOf("p1"), comboRepo)
        setContent(vm)

        compose.onNodeWithTag(TOOL_TAG_SAVE).performClick()
        awaitUntil { vm.state.value.saving }

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag(DIALOG_TAG_SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(DIALOG_TAG_DISCARD).assertIsNotEnabled()
        compose.onNodeWithTag(DIALOG_TAG_KEEP).assertIsNotEnabled()

        gate.complete(Unit)
        awaitUntil { backCalls > 0 }
        compose.waitForIdle()
    }

    @Test
    fun dialogButtonsAreEnabledInNormalState() {
        val vm = newCombo(listOf("p1"), FakeComboRepository())
        setContent(vm)
        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag(DIALOG_TAG_SAVE).assertIsEnabled()
        compose.onNodeWithTag(DIALOG_TAG_DISCARD).assertIsEnabled()
        compose.onNodeWithTag(DIALOG_TAG_KEEP).assertIsEnabled()
    }
}