package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.combo.core.background.ResolvedBackground

/**
 * Единая отрисовка комбо целиком: её используют редактор и превью в списке.
 * Оба экрана рисуют комбо одинаково (WYSIWYG), поэтому порядок «фон, затем рамки»
 * и преобразование холста заданы в одном месте, как и в [drawItem].
 *
 * Экраны различаются только аргументами: редактор передаёт [CanvasTransform] своего
 * вида и `state.items` как есть, список — вписывающее преобразование и кадры,
 * отсортированные по `zIndex` (последний рисуется сверху).
 *
 * Фон приходит уже разрешённым ([ResolvedBackground]): его расчёт зависит от экрана
 * (у редактора холст хранится во `Float`, и округление до `Int` внесло бы погрешность,
 * см. `comboBackgroundOf`).
 *
 * Всё рисуется только внутри прямоугольника холста: рамка может свисать за край (её
 * центр зажат на холсте, а корпус — нет), но за краем её не видно ни на одном экране.
 *
 * Всё, что рисуется вне холста, остаётся на стороне экрана: например, оверлей
 * выделения в редакторе рисуется в экранных координатах, без масштаба.
 */
fun DrawScope.drawCombo(
    t: CanvasTransform,
    canvasW: Float,
    canvasH: Float,
    background: ResolvedBackground,
    frames: List<ItemFrame>,
) {
    translate(t.origin.x, t.origin.y) {
        scale(t.scale, t.scale, pivot = Offset.Zero) {
            clipRect(0f, 0f, canvasW, canvasH) {
                drawComboBackground(background, Offset.Zero, Size(canvasW, canvasH))
                frames.forEach { drawItem(it) }
            }
        }
    }
}
