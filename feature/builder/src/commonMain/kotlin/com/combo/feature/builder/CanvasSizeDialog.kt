package com.combo.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.combo.Const
import com.combo.core.render.CanvasFormat
import com.combo.ui.common.RegisterBackHandler
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcDialog
import com.combo.ui.common.designsystem.components.PcTextField
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CanvasSizeDialog(
    size: CanvasFormat,
    onApply: (CanvasFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    RegisterBackHandler { onDismiss() }
    val ratio = size.aspectRatio()
    val quickSizes = remember(ratio) { quickResolutions(ratio) }
    var widthText by remember(size) { mutableStateOf(size.w.toString()) }
    val width = widthText.trim().toIntOrNull()
    val height = width?.let { it.toLong() / ratio.w * ratio.h }
    val error = when {
        width == null || width <= 0 -> "Enter a positive whole number."
        width % ratio.w != 0 -> "Use a multiple of ${ratio.w} for an exact ${ratio.w}:${ratio.h} ratio."
        height == null || height > Const.MAX_CANVAS_PIXELS / width -> "PNG export supports up to 32 million pixels."
        else -> null
    }
    val result = if (width != null && height != null && error == null) CanvasFormat(width, height.toInt()) else null
    PcDialog(
        title = "Canvas resolution",
        modifier = Modifier.testTag("canvas_size_dialog"),
        onDismissRequest = onDismiss,
        actions = {
            PcButton("Cancel", onDismiss, variant = PcButtonVariant.Ghost)
            PcButton(
                text = "Apply",
                onClick = { result?.let(onApply) },
                enabled = result != null && result != size,
                variant = PcButtonVariant.Primary,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.md)) {
            PcText("Aspect ratio ${ratio.w}:${ratio.h}")
            if (quickSizes.any { it != size }) {
                PcText("Quick sizes", style = PcTheme.typography.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm),
                ) {
                    for (preset in quickSizes) {
                        PcButton(
                            text = "${preset.w} × ${preset.h}",
                            onClick = { onApply(preset) },
                            enabled = preset != size,
                        )
                    }
                }
            }
            PcTextField(
                value = widthText,
                onValueChange = { widthText = it },
                label = "Width (px)",
                modifier = Modifier.fillMaxWidth(),
                error = error,
            )
            PcText(if (result == null) "Height —" else "Height ${result.h} px")
        }
    }
}

/** Target common short-edge sizes, rounding to a whole multiple of the exact ratio. */
private fun quickResolutions(ratio: CanvasFormat): List<CanvasFormat> {
    val shortEdge = minOf(ratio.w, ratio.h)
    return listOf(720, 1080, 1440, 2160).mapNotNull { target ->
        val multiplier = (target.toDouble() / shortEdge).roundToInt().coerceAtLeast(1)
        val width = ratio.w.toLong() * multiplier
        val height = ratio.h.toLong() * multiplier
        if (height <= Const.MAX_CANVAS_PIXELS / width) CanvasFormat(width.toInt(), height.toInt()) else null
    }.distinct()
}
