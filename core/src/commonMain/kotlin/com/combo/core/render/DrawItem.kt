package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.combo.Const
import kotlin.math.roundToInt

data class ContentDrawArgs(
    val image: ImageBitmap,
    val srcW: Int,
    val srcH: Int,
    val scale: Float,
    val pan: Offset = Offset.Zero,
)

data class ItemFrame(
    val border: Border,
    val fillColor: Color,
    val content: ContentDrawArgs,
)

/** ЕДИНСТВЕННЫЙ код отрисовки рамки (редактор и превью списка). */
fun DrawScope.drawItem(f: ItemFrame) {
    val b = f.border.rect()
    clipRect(b.left, b.top, b.right, b.bottom) {
        drawRect(f.fillColor.copy(alpha = Const.FILL_ALPHA), b.topLeft, b.size)
        val a = f.content
        val dst = TransformMath.contentRect(f.border, a.srcW, a.srcH, a.scale, a.pan)
        drawImage(a.image, dstOffset = dst.toIntOffset(), dstSize = dst.toIntSize())
    }
}

private fun Rect.toIntOffset() = IntOffset(left.roundToInt(), top.roundToInt())
private fun Rect.toIntSize() = IntSize(width.roundToInt(), height.roundToInt())

/** Bounds of the photo pixels drawn in the frame, in canvas coordinates. */
fun visiblePhotoBounds(border: Border, srcW: Int, srcH: Int, framing: Framing): Rect {
    if (framing.scale <= 0f || !framing.scale.isFinite()) return Rect.Zero
    val dst = TransformMath.contentRect(border, srcW, srcH, framing.scale, Offset(framing.panX, framing.panY))
    if (!dst.left.isFinite() || !dst.top.isFinite() || !dst.width.isFinite() || !dst.height.isFinite()) return Rect.Zero
    val offset = dst.toIntOffset()
    val size = dst.toIntSize()
    return Rect(
        offset.x.toFloat(),
        offset.y.toFloat(),
        offset.x.toFloat() + size.width,
        offset.y.toFloat() + size.height,
    ).intersect(border.rect())
}
