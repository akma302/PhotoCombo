package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

data class Border(val centerX: Float, val centerY: Float, val width: Float, val height: Float) {
    fun rect() = Rect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
}

/** Грань рамки, за которую тянут при изменении размера. */
enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

data class Viewport(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {
    fun scale(canvas: Size, screen: Size) =
        minOf(screen.width / canvas.width, screen.height / canvas.height) * zoom

    fun toCanvas(screenDelta: Offset, vs: Float) = screenDelta / vs
}

/**
 * Сдвиг вида при зуме «вокруг точки» — чтобы холст не уезжал из-под пальцев.
 *
 * Требуется, чтобы точка холста под центром пальцев осталась под ним же:
 * `canvasPt = (p - O) / vs`, поэтому после изменения нужно
 * `p - O' = (p - O) * f`, откуда `O' = p - (p - O) * f`. Раскрывая `O'` через pan
 * (`O = (S - C * vs) / 2 + pan`, `O' = (S - C * vs * f) / 2 + pan'`), получаем ровно эту
 * формулу. Все величины — экранные координаты.
 *
 * Чистая функция: формулу можно проверить юнит-тестом без UI.
 */
fun viewportZoomPan(
    focus: Offset,
    origin: Offset,
    factor: Float,
    screen: Size,
    canvas: Size,
    vs: Float,
): Offset = Offset(
    (focus.x - (focus.x - origin.x) * factor) - screen.width / 2 + canvas.width * vs * factor / 2,
    (focus.y - (focus.y - origin.y) * factor) - screen.height / 2 + canvas.height * vs * factor / 2,
)

/**
 * Преобразование «экран ↔ холст»: масштаб и положение левого верхнего угла холста в
 * экранных координатах. Масштаб — `min(sw/cw, sh/ch)`, холст центрируется, а в редакторе
 * к этому добавляются зум и сдвиг вида (`viewport.pan`). Редактор и список считают
 * его одним и тем же кодом.
 */
data class CanvasTransform(val scale: Float, val origin: Offset) {

    fun toCanvas(screen: Offset): Offset = Offset(
        (screen.x - origin.x) / scale,
        (screen.y - origin.y) / scale,
    )

    fun toScreen(canvas: Offset): Offset = Offset(
        canvas.x * scale + origin.x,
        canvas.y * scale + origin.y,
    )

    companion object {
        /** Вписать холст в экран по меньшей из сторон и отцентрировать — превью списка. */
        fun fitInto(canvasW: Float, canvasH: Float, screenW: Float, screenH: Float): CanvasTransform {
            val s = minOf(screenW / canvasW, screenH / canvasH)
            return CanvasTransform(s, Offset((screenW - canvasW * s) / 2f, (screenH - canvasH * s) / 2f))
        }

        /** То же + зум и сдвиг пользователя — редактор. */
        fun ofViewport(viewport: Viewport, canvas: Size, screen: Size): CanvasTransform {
            val s = viewport.scale(canvas, screen)
            return CanvasTransform(
                s,
                Offset(
                    (screen.width - canvas.width * s) / 2f + viewport.pan.x,
                    (screen.height - canvas.height * s) / 2f + viewport.pan.y,
                ),
            )
        }
    }
}
