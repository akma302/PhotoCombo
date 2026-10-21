package com.combo.core.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.model.FramingMode
import com.combo.core.model.TransitionType
import com.combo.core.model.Direction

/**
 * Всё, что нужно знать о фото, чтобы нарисовать его в рамке. Собирается адаптерами
 * (см. `data/EntityExt.kt` для Room-сущностей и `feature/builder/draft/ComboDraftMapper.kt`
 * для драфтов) — потому что `core` не знает ни про Room, ни про драфты.
 */
class FrameContent(
    val image: ImageBitmap,
    val srcW: Int,
    val srcH: Int,
    val avgColor: Int,
    val storedScale: Float,
    val storedPanX: Float,
    val storedPanY: Float,
)

/**
 * Единая сборка кадра рамки для редактора, плеера и превью списка. Все три экрана обязаны
 * рисовать одну и ту же рамку одинаково (WYSIWYG), поэтому правила собраны здесь:
 *
 *  - `fillColor` — всегда `avgColor` **входящего** фото;
 *  - если нет `outgoing` **или** `transition`, кадр статичный, без перехода: `drawItem`
 *    при `outgoing == null` всё равно рисует только входящее фото, а анимация без
 *    исходящего кадра смысла не имеет;
 *  - [ContentDrawArgs] собирается через [contentArgsOf], то есть режим рамки (`FIT`)
 *    учитывается в одном месте.
 */
fun buildItemFrame(
    border: Border,
    mode: FramingMode,
    incoming: FrameContent?,
    outgoing: FrameContent? = null,
    transition: TransitionType? = null,
    progress: Float = 1f,
    direction: Direction = Direction.UP,
): ItemFrame? {
    val inContent = incoming ?: return null
    val fill = Color(inContent.avgColor)
    fun args(c: FrameContent) = contentArgsOf(
        mode = mode,
        image = c.image,
        border = border,
        srcW = c.srcW,
        srcH = c.srcH,
        storedScale = c.storedScale,
        storedPanX = c.storedPanX,
        storedPanY = c.storedPanY,
    )

    val outContent = outgoing
    if (outContent == null || transition == null) {
        return ItemFrame(
            border = border,
            fillColor = fill,
            incoming = args(inContent),
        )
    }
    return ItemFrame(
        border = border,
        fillColor = fill,
        incoming = args(inContent),
        outgoing = args(outContent),
        transition = transition.create(),
        progress = progress,
        direction = direction,
    )
}