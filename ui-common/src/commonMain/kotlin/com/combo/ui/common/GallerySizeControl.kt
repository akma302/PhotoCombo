package com.combo.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import kotlin.math.roundToInt

@Composable
fun GallerySizeControl(
    state: GalleryScaleUiState,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.xs)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PcText("Item size", style = PcTheme.typography.label)
            PcSlider(state.percent, 50f..200f, onChange, onFinished, "Item size",
                Modifier.width(180.dp).testTag("gallery_size_slider"), enabled = state.loaded)
            PcText("${state.percent.roundToInt()}%", Modifier.widthIn(min = 48.dp),
                style = PcTheme.typography.label)
            PcButton("Reset", onReset, Modifier.testTag("gallery_size_reset"),
                variant = PcButtonVariant.Ghost, enabled = state.loaded)
        }
        if (state.saveFailed) PcText(
            "Couldn't save item size. Change it or reset to retry.",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = PcTheme.colors.danger,
        )
    }
}
