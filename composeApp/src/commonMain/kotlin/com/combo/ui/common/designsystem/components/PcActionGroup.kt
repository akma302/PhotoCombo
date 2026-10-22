package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.combo.ui.common.designsystem.theme.PcTheme

/** Команды переносятся целиком; при ограниченной высоте доступны через прокрутку. */
@Composable
fun PcActionGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        if (constraints.hasBoundedHeight) {
            val gutter = PcTheme.sizes.focusOffset + PcTheme.strokes.focus
            PcActionFlow(Modifier.verticalScroll(rememberScrollState()).padding(gutter), content)
        } else {
            PcActionFlow(Modifier, content)
        }
    }
}

/** Поток измеряет полную высоту; ограничение и прокрутка принадлежат внешней области. */
@Composable
private fun PcActionFlow(modifier: Modifier, content: @Composable () -> Unit) {
    val gap = PcTheme.spacing.sm
    Layout(content, modifier) { measurables, constraints ->
        val space = gap.roundToPx()
        val children = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val positions = mutableListOf<Pair<Int, Int>>()
        var x = 0
        var y = 0
        var lineHeight = 0
        var usedWidth = 0
        for (child in children) {
            if (x > 0 && child.width > constraints.maxWidth - x) {
                y += lineHeight + space
                x = 0
                lineHeight = 0
            }
            positions += x to y
            usedWidth = maxOf(usedWidth, x + child.width)
            x += child.width + space
            lineHeight = maxOf(lineHeight, child.height)
        }
        layout(constraints.constrainWidth(usedWidth), constraints.constrainHeight(y + lineHeight)) {
            children.forEachIndexed { index, child ->
                val (left, top) = positions[index]
                child.placeRelative(left, top)
            }
        }
    }
}
