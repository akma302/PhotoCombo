package com.combo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.combo.data.ComboWithItems
import com.combo.feature.combo_list.ComboListScreen
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.feature.combo_list.NEW_COMBO_TAG
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * В секции комбо всегда есть рабочий вход в редактор: и на пустом списке (когда
 * редактировать нечего), и на непустом.
 */
@OptIn(ExperimentalTestApi::class)
class ComboListOpensBuilderTest : UiTestBase() {

    @Test
    fun combosSectionHasWorkingEntryPointToBuilder() = runComposeUiTest {
        val comboRepo = FakeComboRepository()
        val vm = ComboListViewModel(comboRepo, FakePhotoRepository(), StubBitmapSource())
        val koin = trackKoin(FakePhotoRepository())

        var createRequested = 0
        var opened: String? = null

        setContent {
            TestScreen(koin, backDispatcher) {
                ComboListScreen(
                    onOpenBuilder = { opened = it },
                    onCreateCombo = { createRequested++ },
                    vm = vm,
                )
            }
        }

        // Пустой список комбо: единственный способ начать — кнопка «New combo».
        onNodeWithText("No combos yet").assertIsDisplayed()
        onNodeWithTag(NEW_COMBO_TAG).assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, createRequested)

        // Непустой список: точка входа в создание нового комбо не исчезает.
        comboRepo.emit(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Trip"),
                    items = emptyList(),
                ),
            ),
        )
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Trip").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(NEW_COMBO_TAG).assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(2, createRequested)

        // Карточка комбо открывает редактор; кнопки воспроизведения в списке нет.
        onNodeWithContentDescription("Edit").performClick()
        waitForIdle()
        assertEquals("c1", opened)
        onAllNodesWithContentDescription("Play").assertCountEquals(0)
    }
}
