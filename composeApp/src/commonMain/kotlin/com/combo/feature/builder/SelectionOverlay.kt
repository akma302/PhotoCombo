package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.combo.core.render.CanvasTransform

/**
 * Оверлей выделенной рамки — в ЭКРАННЫХ координатах: сдвиг есть, масштаба нет (рамка
 * обводится ровно по своим экранным границам, а её грани изменяются жестом). Поэтому он
 * нарисован на стороне экрана, а не внутри `drawCombo`: у того весь блок обёрнут в
 * `scale`, и обводка вышла бы толще ровно во столько раз, во сколько увеличен вид.
 *
 * Обводка и маркеры обрезаны по экранному прямоугольнику холста: свисающая за край рамка
 * обводится только в видимой части, как и сама рамка (обрезка — в `drawCombo`). Захват
 * при этом не страдает: он не рисуется, а полоса захвата грани не ограничена холстом.
 *
 * Расширение [DrawScope], а не `@Composable`: состояние и цвет приходят аргументами,
 * поэтому функция не зависит ни от композиции, ни от темы, а вызывается прямо из
 * draw-лямбды `Canvas` (почему кадры собираются именно внутри отрисовки — см. комментарий
 * в `EditorCanvas.kt`).
 *
 * Маркеры размера рисуются только в режиме [EditMode.BORDER]. В режиме EDIT рамку двигают
 * и масштабируют жестами за любую её точку, так что маркеры там означали бы действие,
 * которого нет.
 */
internal fun DrawScope.drawSelectionOverlay(
    state: BuilderUiState,
    transform: CanvasTransform,
    color: Color,
) {
    val sel = state.items.firstOrNull { it.id == state.selectedItemId } ?: return
    val r = sel.border().rect()
    val canvas = Rect(
        offset = transform.origin,
        size = Size(state.canvasW * transform.scale, state.canvasH * transform.scale),
    )
    clipRect(canvas.left, canvas.top, canvas.right, canvas.bottom) {
        translate(transform.origin.x, transform.origin.y) {
            drawRect(
                color = color,
                topLeft = Offset(r.left * transform.scale, r.top * transform.scale),
                size = Size(r.width * transform.scale, r.height * transform.scale),
                style = Stroke(width = 2.dp.toPx()),
            )
            if (state.mode == EditMode.BORDER) {
                listOf(r.center.x to r.top, r.right to r.center.y, r.center.x to r.bottom, r.left to r.center.y)
                    .forEach { (x, y) ->
                        drawCircle(
                            color = color,
                            radius = 4.dp.toPx(),
                            center = Offset(x * transform.scale, y * transform.scale),
                        )
                    }
            }
        }
    }
}
