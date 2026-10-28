package com.combo.ui.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.*
import com.combo.ui.common.designsystem.theme.PcTheme

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    PcDesktopTheme { PcSurface(Modifier.padding(8.dp), color = PcTheme.colors.app) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } }
}

@Preview(name = "Button variants") @Composable
internal fun ButtonPreview() = PreviewSurface {
    PcButtonVariant.entries.forEach { PcButton(it.name, {}, variant = it) }
    PcButton("Disabled", {}, enabled = false)
    PcButton("Busy", {}, busy = true)
}

@Preview(name = "Icon button") @Composable
internal fun IconButtonPreview() = PreviewSurface { Row {
    PcIconButton(PcIcons.Add, "Add", {})
    PcIconButton(PcIcons.Delete, "Delete", {}, enabled = false)
} }

@Preview(name = "Checkbox") @Composable
internal fun CheckboxPreview() = PreviewSurface {
    PcCheckbox(true, {}, "Selected")
    PcCheckbox(false, {}, "Unselected")
    PcCheckbox(false, {}, "Disabled", enabled = false)
}

@Preview(name = "Text field") @Composable
internal fun TextFieldPreview() = PreviewSurface {
    PcTextField("PhotoCombo", {}, "Name")
    PcTextField("", {}, "Required", error = "Enter a name")
    PcTextField("Unavailable", {}, "Disabled", enabled = false)
}

@Preview(name = "Slider") @Composable
internal fun SliderPreview() = PreviewSurface {
    PcSlider(100f, 50f..200f, {}, {}, "Item size", Modifier.width(240.dp))
    PcSlider(100f, 50f..200f, {}, {}, "Disabled", Modifier.width(240.dp), enabled = false)
}

@Preview(name = "Segmented control") @Composable
internal fun SegmentedPreview() = PreviewSurface {
    PcSegmentedControl(listOf(PcOption("Frame", "Frame"), PcOption("Photo", "Photo")), "Frame", {})
}

@Preview(name = "Select") @Composable
internal fun SelectPreview() = PreviewSurface {
    PcSelect("16:9", listOf(PcOption("16:9", "16:9"), PcOption("1:1", "1:1")), {}, "Aspect")
}

@Preview(name = "Action group") @Composable
internal fun ActionGroupPreview() = PreviewSurface {
    PcActionGroup(Modifier.width(180.dp)) { PcButton("Fit", {}); PcButton("Fill", {}); PcButton("Replace", {}) }
}

@Preview(name = "Tooltip") @Composable
internal fun TooltipPreview() = PreviewSurface { PcTooltip("More information") { PcButton("Hover or focus", {}) } }

@Preview(name = "Dialog") @Composable
internal fun DialogPreview() = PreviewSurface {
    PcDialog("Save changes?", {}, actions = { PcButton("Save", {}) }) { PcText("Save this combo?") }
}

@Preview(name = "Text") @Composable
internal fun TextPreview() = PreviewSurface {
    PcText("Title", style = PcTheme.typography.title)
    PcText("Body", style = PcTheme.typography.body)
    PcText("Secondary", color = PcTheme.colors.textSecondary)
}

@Preview(name = "Surface") @Composable
internal fun SurfacePreview() = PreviewSurface { PcSurface { PcText("Panel", Modifier.padding(16.dp)) } }

@Preview(name = "Divider") @Composable
internal fun DividerPreview() = PreviewSurface { PcText("Above"); PcDivider(Modifier.width(200.dp)); PcText("Below") }

@Preview(name = "Icon") @Composable
internal fun IconPreview() = PreviewSurface { Row { PcIcon(PcIcons.Photo); PcIcon(PcIcons.Add); PcIcon(PcIcons.Delete) } }

@Preview(name = "Control layout") @Composable
internal fun ControlLayoutPreview() = PreviewSurface { PcControlLayout { PcText("Control") } }
