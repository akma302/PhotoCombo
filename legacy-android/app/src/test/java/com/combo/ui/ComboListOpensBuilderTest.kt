package com.combo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.data.ComboWithItems
import com.combo.feature.combo_list.ComboListScreen
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.feature.combo_list.NEW_COMBO_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Баг 2: из секции Combos не было НИКАКОГО способа попасть в combo builder —
 * кнопка «New combo» в списке комбо отсутствовала, а переход в builder существовал
 * только из Library (мультиселект фото) и из карточки существующего комбо.
 *
 * Проверяем наличие и работоспособность точки входа и для пустого списка комбо
 * (где пользователь не может отредактировать ни один существующий), и для непустого.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComboListOpensBuilderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun combosSectionHasWorkingEntryPointToBuilder() {
        val comboRepo = FakeComboRepository()
        val vm = ComboListViewModel(comboRepo, FakePhotoRepository(), FakeBitmapSource())

        var createRequested = 0

        compose.setContent {
            ComboListScreen(
                onOpenBuilder = {},
                onCreateCombo = { createRequested++ },
                onPlay = {},
                vm = vm,
            )
        }

        // Пустой список комбо: единственный способ начать — кнопка «New combo».
        compose.onNodeWithText("No combos yet").assertIsDisplayed()
        compose.onNodeWithTag(NEW_COMBO_TAG).assertIsDisplayed().performClick()
        compose.waitForIdle()
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
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Trip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(NEW_COMBO_TAG).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(2, createRequested)
    }
}
