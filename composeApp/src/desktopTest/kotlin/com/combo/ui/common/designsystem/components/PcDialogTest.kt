package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PcDialogTest {
    @Test
    fun dialogReceivesAndTrapsFocusAndEscapeRestoresInitiator() = runSkikoComposeUiTest(Size(600f, 400f)) {
        val open = mutableStateOf(false)
        var dismissals = 0
        setContent {
            PcTheme {
                val caller = remember { FocusRequester() }
                Box(Modifier.padding(32.dp)) {
                    PcText("Open", Modifier.focusRequester(caller).clickable { open.value = true })
                }
                if (open.value) PcDialog("Confirm", onDismissRequest = { dismissals++; open.value = false },
                    returnFocus = caller, actions = {
                        PcCheckbox(false, { open.value = false }, "Cancel")
                        PcCheckbox(false, {}, "Accept")
                    }) { PcText("Dialog body") }
            }
        }
        onNodeWithText("Open").performClick()
        onNodeWithText("Cancel").assertIsFocused()
        onNodeWithText("Cancel").performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Accept").assertIsFocused()
        onNodeWithText("Accept").performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Cancel").assertIsFocused()
        onNodeWithText("Cancel").performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, dismissals)
        onNodeWithText("Confirm").assertDoesNotExist()
        onNodeWithText("Open").assertIsFocused()
    }
}
