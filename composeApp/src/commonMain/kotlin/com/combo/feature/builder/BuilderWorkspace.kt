package com.combo.feature.builder

import androidx.compose.foundation.layout.fillMaxSize
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.primitives.PcSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Минимум оставляет широкому редактору холст не уже 520 dp рядом с инспектором 240 dp. */
internal val BUILDER_WIDE_MIN = 760.dp

/**
 * Холст и инспектор остаются теми же узлами композиции при смене размера окна.
 * Перенос меняет только измерение и размещение: pointerInput холста не перезапускается.
 */
@Composable
internal fun BuilderWorkspace(
    wide: Boolean,
    modifier: Modifier = Modifier,
    canvas: @Composable () -> Unit,
    inspector: @Composable () -> Unit,
) {
    val border = PcTheme.colors.border
    Layout(
        modifier = modifier,
        content = {
            PcSurface(Modifier.fillMaxSize()) { canvas() }
            PcSurface(
                modifier = Modifier.testTag(BUILDER_INSPECTOR_TAG).drawWithContent {
                    drawContent()
                    if (wide) drawLine(border, Offset.Zero, Offset(0f, size.height))
                    else drawLine(border, Offset.Zero, Offset(size.width, 0f))
                },
            ) { inspector() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val panel = if (wide) {
            measurables[1].measure(Constraints.fixed(240.dp.roundToPx().coerceAtMost(width), height))
        } else {
            measurables[1].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = minOf(height / 2, 224.dp.roundToPx())))
        }
        val drawing = measurables[0].measure(
            Constraints.fixed(if (wide) width - panel.width else width, if (wide) height else height - panel.height),
        )
        layout(width, height) {
            drawing.placeRelative(0, 0)
            panel.placeRelative(if (wide) drawing.width else 0, if (wide) 0 else drawing.height)
        }
    }
}
