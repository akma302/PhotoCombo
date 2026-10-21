package com.combo.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.combo.core.render.CanvasTransform
import kotlin.math.roundToInt

/**
 * Точки «страниц» выбранной рамки — переключение активного фото тапом.
 *
 * Позиционируются по ЭКРАННЫМ координатам нижней стороны рамки (те же `origin` и `scale`,
 * что у холста), поэтому полоса точек едет вместе с зумом и сдвигом вида. Показываются
 * только при двух и более фото в рамке: переключать на одной странице нечего, а пустая
 * рамка точек вовсе не имеет.
 */
@Composable
internal fun PageDots(vm: ComboBuilderViewModel, state: BuilderUiState, transform: CanvasTransform) {
    val item = state.items.firstOrNull { it.id == state.selectedItemId } ?: return
    if (item.contents.size < 2) return
    val b = item.border()
    val rect = b.rect()
    val activeIdx = vm.activeIndexOf(item)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .offset {
                IntOffset(
                    (transform.origin.x + rect.center.x * transform.scale).roundToInt(),
                    (transform.origin.y + rect.bottom * transform.scale + 12.dp.toPx()).roundToInt(),
                )
            },
    ) {
        item.contents.forEachIndexed { i, _ ->
            Box(
                modifier = Modifier
                    .size(if (i == activeIdx) 10.dp else 8.dp)
                    .background(
                        color = if (i == activeIdx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { vm.onEvent(BuilderEvent.SetActive(item.id, i)) },
            )
        }
    }
}