package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Геометрия фото внутри рамки. Редактор и превью считают положение фото только
 * этими формулами, поэтому на всех экранах картинка совпадает (WYSIWYG).
 */
object TransformMath {
    /** Размер фото на холсте при масштабе [scale] (размеры исходника — в пикселях). */
    fun contentSize(srcW: Int, srcH: Int, scale: Float) = Size(srcW * scale, srcH * scale)

    /** Прямоугольник фото на холсте: центр рамки плюс сдвиг [pan], размер — [contentSize]. */
    fun contentRect(b: Border, srcW: Int, srcH: Int, scale: Float, pan: Offset): Rect {
        val s = contentSize(srcW, srcH, scale)
        val cx = b.centerX + pan.x
        val cy = b.centerY + pan.y
        return Rect(cx - s.width / 2, cy - s.height / 2, cx + s.width / 2, cy + s.height / 2)
    }

    /**
     * Мягкое ограничение сдвига: фото следует за пальцем, но не уходит из рамки целиком
     * (на пределе его край лишь касается края рамки).
     */
    fun clampPan(pan: Offset, b: Border, content: Size) = Offset(
        pan.x.coerceIn(-(b.width + content.width) / 2, (b.width + content.width) / 2),
        pan.y.coerceIn(-(b.height + content.height) / 2, (b.height + content.height) / 2)
    )

    /** Масштаб «вписать целиком»: фото помещается в рамку полностью, по краям остаются поля. */
    fun fitScale(b: Border, src: Size) = minOf(b.width / src.width, b.height / src.height)

    /** Масштаб «заполнить»: фото закрывает рамку целиком, лишнее обрезается. */
    fun fillScale(b: Border, src: Size) = maxOf(b.width / src.width, b.height / src.height)

    /**
     * Потолок масштаба фото: `scale * viewportScale ≤ cap`. Дальше картинка увеличивается
     * больше чем на [cap] экранных пикселей на пиксель исходника и «мылится».
     */
    fun maxContentScale(viewportScale: Float, cap: Float = 1.5f) = cap / viewportScale
}
