package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.combo.Const
import kotlin.math.roundToInt
import com.combo.core.model.Direction

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
    val incoming: ContentDrawArgs,
    val outgoing: ContentDrawArgs? = null,
    val transition: ContentTransition? = null,
    val progress: Float = 1f,
    val direction: Direction = Direction.UP,
)

/** ЕДИНСТВЕННЫЙ код отрисовки item (builder + viewer + экспорт). */
fun DrawScope.drawItem(f: ItemFrame) {
    val b = f.border.rect()
    clipRect(b.left, b.top, b.right, b.bottom) {
        drawRect(f.fillColor.copy(alpha = Const.FILL_ALPHA), b.topLeft, b.size)
        if (f.outgoing == null || f.transition == null) {
            drawContent(f.incoming, ContentDrawSpec.IDENTITY, f)
        } else {
            drawContent(f.outgoing, f.transition.outgoing(f.progress, f.direction), f)
            drawContent(f.incoming, f.transition.incoming(f.progress, f.direction), f)
        }
    }
}

private fun DrawScope.drawContent(a: ContentDrawArgs, s: ContentDrawSpec, f: ItemFrame) {
    val b = f.border.rect()
    val dst = TransformMath.contentRect(f.border, a.srcW, a.srcH, a.scale, a.pan)
    // scale(..., pivot = b.center) уже масштабирует вокруг центра рамки. Не оборачивать
    // его ещё и в translate(b.center) { translate(-b.center) { ... } }: центрирование
    // применилось бы дважды, и для рамки не в начале координат рисунок сместился бы на
    // center * (1 - scale). Это незаметно у переходов, которые не меняют scale
    // (None/Fade/Slide/Wipe), и ломает положение при Zoom — единственном, который меняет.
    translate(s.offsetNorm.x * b.width, s.offsetNorm.y * b.height) {
        scale(s.scale, s.scale, pivot = b.center) {
            if (s.wipe < 0) {
                drawImage(a.image, dstOffset = dst.toIntOffset(), dstSize = dst.toIntSize(), alpha = s.alpha)
            } else {
                val wr = wipeRect(b, s.wipe, f.direction)
                clipRect(wr.left, wr.top, wr.right, wr.bottom) {
                    drawImage(a.image, dstOffset = dst.toIntOffset(), dstSize = dst.toIntSize(), alpha = s.alpha)
                }
            }
        }
    }
}

/** Шторка растёт с края, откуда входит контент. */
private fun wipeRect(b: Rect, w: Float, dir: Direction): Rect = when (dir) {
    Direction.UP -> Rect(b.left, b.bottom - b.height * w, b.right, b.bottom)
    Direction.DOWN -> Rect(b.left, b.top, b.right, b.top + b.height * w)
    Direction.LEFT -> Rect(b.right - b.width * w, b.top, b.right, b.bottom)
    Direction.RIGHT -> Rect(b.left, b.top, b.left + b.width * w, b.bottom)
}

private fun Rect.toIntOffset() = IntOffset(left.roundToInt(), top.roundToInt())
private fun Rect.toIntSize() = IntSize(width.roundToInt(), height.roundToInt())
