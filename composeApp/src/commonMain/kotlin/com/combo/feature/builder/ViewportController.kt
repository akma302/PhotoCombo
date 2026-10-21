package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.Const
import com.combo.core.render.Viewport
import com.combo.core.render.viewportZoomPan

/**
 * Зум вида «вокруг точки» (щипок в редакторе): точка холста под пальцами остаётся на
 * месте. Возвращает новый [Viewport], исходный не изменяется.
 *
 * Порядок вычислений важен:
 *
 * 1. [Viewport.scale] берётся ДО изменения зума: сдвиг считается от старого масштаба,
 *    иначе холст «уезжает» из-под пальцев.
 * 2. `origin` — экранное положение левого верхнего угла холста при старом зуме.
 * 3. Новый зум зажимается в [Const.VIEW_ZOOM_MIN] .. [Const.VIEW_ZOOM_MAX] (0.5 .. 4).
 * 4. Панорама считается от ПРИМЕНЁННОГО множителя (`zoom / this.zoom`), а не от
 *    запрошенного. Когда зум упёрся в границу, холст не должен «дёргаться» на последнем
 *    кадре жеста.
 * 5. Применённый множитель — это отношение к СТАРОМУ зуму, а не запрошенный `factor`:
 *    поэтому `factor = 0` даёт зум `Const.VIEW_ZOOM_MIN` и конечную панораму, а не
 *    NaN от деления 0/0.
 *
 * @param screen размер экрана в пикселях; вызывающий обязан нормализовать его (в VM это
 *   `Size(screenW.coerceAtLeast(1f), screenH.coerceAtLeast(1f))`), иначе до первого
 *   измерения вьюхи панорама считается от нулевого размера экрана.
 * @param canvas размер холста; [Viewport.scale] делит на его стороны, поэтому нулевой
 *   холст даст бесконечный масштаб — это ошибка вызывающего, а не зума.
 */
internal fun Viewport.zoomedAround(
    factor: Float,
    focus: Offset,
    screen: Size,
    canvas: Size,
): Viewport {
    val vsBefore = scale(canvas, screen)
    val origin = Offset(
        (screen.width - canvas.width * vsBefore) / 2f + pan.x,
        (screen.height - canvas.height * vsBefore) / 2f + pan.y,
    )
    val zoom = (this.zoom * factor).coerceIn(Const.VIEW_ZOOM_MIN, Const.VIEW_ZOOM_MAX)
    val applied = zoom / this.zoom
    val pan = viewportZoomPan(
        focus = focus,
        origin = origin,
        factor = applied,
        screen = screen,
        canvas = canvas,
        vs = vsBefore,
    )
    return Viewport(zoom = zoom, pan = pan)
}
