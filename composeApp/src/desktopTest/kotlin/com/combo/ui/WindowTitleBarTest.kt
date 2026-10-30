package com.combo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.combo.ui.common.PcDesktopTheme
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class WindowTitleBarTest {
    @Test fun actionsAndRestoreState() = runSkikoComposeUiTest(Size(420f, 80f)) {
        var maximized by mutableStateOf(false)
        var minimized = 0
        var closed = 0
        setContent {
            PcDesktopTheme {
                WindowTitleBar(maximized, { minimized++ }, { maximized = !maximized }, { closed++ })
            }
        }
        onNodeWithContentDescription("Minimize").assertIsDisplayed().performClick()
        onNodeWithContentDescription("Maximize").assertIsDisplayed().performClick()
        onNodeWithContentDescription("Restore").assertIsDisplayed().performClick()
        onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
        runOnIdle {
            assertEquals(1, minimized)
            assertEquals(1, closed)
            assertEquals(false, maximized)
        }
    }
}
