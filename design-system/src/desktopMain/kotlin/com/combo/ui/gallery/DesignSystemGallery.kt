package com.combo.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.ui.common.PcDesktopFonts
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.*
import com.combo.ui.common.designsystem.theme.PcTheme

data class GalleryExample(val name: String, val content: @Composable () -> Unit)

private val componentNames = listOf("Button", "Icon button", "Checkbox", "Text field", "Slider",
    "Segmented control", "Select", "Tooltip", "Dialog", "Action group", "Text", "Surface", "Divider", "Icons")

@Composable
fun DesignSystemGallery(extraExamples: List<GalleryExample> = emptyList()) {
    var selected by remember { mutableStateOf("All") }
    val names = listOf("All") + componentNames + extraExamples.map { it.name }
    require(names.distinct().size == names.size) { "Gallery example names must be unique" }
    val active = selected.takeIf { it in names } ?: "All"
    PcSurface(Modifier.fillMaxSize(), color = PcTheme.colors.app) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(PcTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.md)) {
                    PcText("Component", style = PcTheme.typography.label)
                    PcSelect(active, names.map { PcOption(it, it) }, { selected = it }, "Gallery component",
                        Modifier.testTag("gallery_component_selector"))
                }
                PcText("Select a component for a focused view", color = PcTheme.colors.textSecondary,
                    style = PcTheme.typography.caption)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (active == "All") OverviewGallery()
                else extraExamples.firstOrNull { it.name == active }?.content?.invoke() ?: ComponentExample(active)
            }
        }
    }
}

@Composable
private fun OverviewGallery() {
    var sliderValue by remember { mutableStateOf(100f) }
    var checked by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("Frame") }
    var text by remember { mutableStateOf("Weekend combo") }
    var requiredName by remember { mutableStateOf("") }
    var aspect by remember { mutableStateOf("16:9") }
    var dialog by remember { mutableStateOf(false) }
    PcSurface(Modifier.fillMaxSize().testTag("pc_gallery"), color = PcTheme.colors.app) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PcTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.lg)) {
            PcText("PhotoCombo", style = PcTheme.typography.title)
            PcText("UI components · Dark · ${PcDesktopFonts.resolvedFamily}", color = PcTheme.colors.textSecondary,
                style = PcTheme.typography.caption)
            PcDivider()
            PcText("Surfaces & type", style = PcTheme.typography.title)
            PcActionGroup {
                listOf("App" to PcTheme.colors.app, "Panel" to PcTheme.colors.panel,
                    "Workspace" to PcTheme.colors.workspace, "Accent" to PcTheme.colors.accent).forEach { (name, color) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                        Box(Modifier.size(20.dp).background(color, PcTheme.shapes.small))
                        PcText(name, style = PcTheme.typography.label)
                    }
                }
            }
            PcText("Build a composition", style = PcTheme.typography.title)
            PcText("Primary text and explanatory copy", style = PcTheme.typography.body)
            PcText("Secondary label · 100%", style = PcTheme.typography.caption, color = PcTheme.colors.textSecondary)
            PcText("Buttons", style = PcTheme.typography.title)
            PcActionGroup {
                PcButton("Save", {}, Modifier.testTag("gallery_primary"), PcButtonVariant.Primary, icon = PcIcons.Check)
                PcButton("Add photo", {}, icon = PcIcons.Add)
                PcButton("Replace", {}, variant = PcButtonVariant.Ghost)
                PcButton("Delete", {}, variant = PcButtonVariant.Danger, icon = PcIcons.Delete)
                PcButton("Delete permanently", {}, variant = PcButtonVariant.DangerFilled, icon = PcIcons.Delete)
                PcButton("Unavailable", {}, enabled = false)
                PcButton("Saving", {}, busy = true)
            }
            PcText("Native vectors · 16 / 20 dp", style = PcTheme.typography.title)
            PcActionGroup {
                galleryIcons().forEach { (name, vector) ->
                    Column(verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.xs)) {
                            PcIcon(vector)
                            PcIcon(vector, size = PcTheme.sizes.iconLarge)
                            PcIcon(vector, tint = PcTheme.colors.text.copy(alpha = PcTheme.opacity.disabled))
                        }
                        PcText(name, style = PcTheme.typography.caption, color = PcTheme.colors.textSecondary)
                    }
                }
            }
            PcText("Item size · ${sliderValue.toInt()}%", style = PcTheme.typography.label)
            PcSlider(sliderValue, 50f..200f, { sliderValue = it }, {}, "Item size", Modifier.widthIn(max = 360.dp).fillMaxWidth())
            PcSlider(100f, 50f..200f, {}, {}, "Disabled size", Modifier.widthIn(max = 360.dp).fillMaxWidth(), enabled = false)
            PcText("Selection & commands", style = PcTheme.typography.title)
            PcSegmentedControl(listOf(PcOption("Frame", "Frame", PcIcons.Frame), PcOption("Photo", "Photo", PcIcons.Photo)),
                mode, { mode = it }, Modifier.widthIn(max = 360.dp))
            PcActionGroup {
                PcButton("Fit", {})
                PcButton("Fill", {})
                PcIconButton(PcIcons.LayerUp, "Move forward", {})
                PcIconButton(PcIcons.LayerDown, "Move backward", {})
            }
            PcActionGroup {
                PcCheckbox(checked, { checked = it }, "Always fit")
                PcCheckbox(false, {}, "Unavailable", enabled = false)
            }
            PcText("Fields & overlays", style = PcTheme.typography.title)
            PcTextField(text, { text = it }, "Name", Modifier.widthIn(max = 420.dp).fillMaxWidth())
            PcTextField(requiredName, { requiredName = it }, "Required name", Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                error = if (requiredName.isBlank()) "Enter a name" else null)
            PcSelect(aspect, listOf("16:9", "4:3", "1:1").map { PcOption(it, it) }, { aspect = it }, "Canvas aspect")
            PcActionGroup {
                PcTooltip("This is a keyboard-accessible action") { PcButton("Hover or focus", {}) }
                PcButton("Open dialog", { dialog = true })
            }
            PcDivider()
            PcText("Foundation input · Custom drawing · No application data", style = PcTheme.typography.caption,
                color = PcTheme.colors.textSecondary)
        }
    }
    if (dialog) PcDialog("Save composition?", { dialog = false }, actions = {
        PcButton("Cancel", { dialog = false }, variant = PcButtonVariant.Ghost)
        PcButton("Save", { dialog = false }, variant = PcButtonVariant.Primary)
    }) { PcText("The dialog traps keyboard focus and returns it when closed.") }
}

private fun galleryIcons(): List<Pair<String, ImageVector>> = listOf(
    "Back" to PcIcons.Back, "Undo" to PcIcons.Undo, "Redo" to PcIcons.Redo, "Add" to PcIcons.Add,
    "Remove" to PcIcons.Remove, "Check" to PcIcons.Check, "Close" to PcIcons.Close,
    "Chevron" to PcIcons.ChevronDown, "Photo" to PcIcons.Photo, "Replace" to PcIcons.ReplacePhoto,
    "Delete" to PcIcons.Delete, "Layer up" to PcIcons.LayerUp, "Layer down" to PcIcons.LayerDown,
    "Canvas" to PcIcons.WholeCanvas, "Aspect" to PcIcons.AspectRatio, "Palette" to PcIcons.Palette,
    "Frame" to PcIcons.Frame, "Crop" to PcIcons.Crop, "Info" to PcIcons.Info, "Warning" to PcIcons.Warning,
)
