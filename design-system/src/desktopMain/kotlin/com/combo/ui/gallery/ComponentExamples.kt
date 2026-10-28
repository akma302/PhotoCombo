package com.combo.ui.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.*
import com.combo.ui.common.designsystem.theme.PcTheme

/** A focused, interactive example; local state never touches application data. */
@Composable
internal fun ComponentExample(name: String) {
    var checked by remember(name) { mutableStateOf(true) }
    var text by remember(name) { mutableStateOf("") }
    var value by remember(name) { mutableStateOf(100f) }
    var mode by remember(name) { mutableStateOf("Frame") }
    var dialog by remember(name) { mutableStateOf(false) }
    PcSurface(Modifier.fillMaxSize().testTag("gallery_example"), color = PcTheme.colors.app) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PcTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.md)) {
            PcText(name, style = PcTheme.typography.title)
            when (name) {
                "Button" -> {
                    PcButtonVariant.entries.forEach { variant -> PcButton(variant.name, {}, variant = variant) }
                    PcButton("Disabled", {}, enabled = false)
                    PcButton("Busy", {}, busy = true)
                }
                "Icon button" -> Row { PcIconButton(PcIcons.Add, "Add", {}); PcIconButton(PcIcons.Delete, "Delete", {}, enabled = false) }
                "Checkbox" -> { PcCheckbox(checked, { checked = it }, "Selected"); PcCheckbox(false, {}, "Disabled", enabled = false) }
                "Text field" -> {
                    PcTextField(text, { text = it }, "Name")
                    PcTextField("", {}, "Required name", error = "Enter a name")
                    PcTextField("Disabled", {}, "Disabled", enabled = false)
                }
                "Slider" -> {
                    PcText("Value: ${value.toInt()}%")
                    PcSlider(value, 50f..200f, { value = it }, {}, "Item size", Modifier.widthIn(max = 320.dp).fillMaxWidth())
                    PcSlider(100f, 50f..200f, {}, {}, "Disabled", Modifier.widthIn(max = 320.dp).fillMaxWidth(), enabled = false)
                }
                "Segmented control" -> PcSegmentedControl(listOf(PcOption("Frame", "Frame"), PcOption("Photo", "Photo")), mode, { mode = it })
                "Select" -> PcSelect(mode, listOf(PcOption("Frame", "Frame"), PcOption("Photo", "Photo")), { mode = it }, "Mode")
                "Tooltip" -> PcTooltip("More information") { PcButton("Hover or focus", {}) }
                "Dialog" -> PcButton("Open dialog", { dialog = true })
                "Action group" -> PcActionGroup(Modifier.widthIn(max = 180.dp)) { PcButton("Fit", {}); PcButton("Fill", {}); PcButton("Replace", {}) }
                "Text" -> { PcText("Title", style = PcTheme.typography.title); PcText("Body", style = PcTheme.typography.body); PcText("Secondary", color = PcTheme.colors.textSecondary) }
                "Surface" -> PcSurface { PcText("Panel", Modifier.padding(PcTheme.spacing.lg)) }
                "Divider" -> { PcText("Above"); PcDivider(Modifier.width(200.dp)); PcText("Below") }
                "Icons" -> PcActionGroup { listOf(PcIcons.Add, PcIcons.Photo, PcIcons.Delete, PcIcons.Back).forEach { PcIcon(it) } }
            }
        }
    }
    if (dialog) PcDialog("Save composition?", { dialog = false }, actions = {
        PcButton("Cancel", { dialog = false }, variant = PcButtonVariant.Ghost)
        PcButton("Save", { dialog = false }, variant = PcButtonVariant.Primary)
    }) { PcText("The dialog traps keyboard focus and returns it when closed.") }
}
