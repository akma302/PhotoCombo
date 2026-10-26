package com.combo.core.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.model.FramingMode

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
 * Единая сборка кадра рамки для редактора и превью списка. Оба экрана обязаны рисовать
 * одну и ту же рамку одинаково (WYSIWYG), поэтому правила собраны здесь:
 *
 *  - `fillColor` — `avgColor` фото рамки;
 *  - [ContentDrawArgs] собирается через [contentArgsOf], то есть режим рамки (`FIT`)
 *    учитывается в одном месте.
 *
 * Фото ещё не загружено ([content] == `null`) — кадра нет, рамку пока не рисуем.
 */
fun buildItemFrame(
    border: Border,
    mode: FramingMode,
    content: FrameContent?,
): ItemFrame? {
    val c = content ?: return null
    return ItemFrame(
        border = border,
        fillColor = Color(c.avgColor),
        content = contentArgsOf(
            mode = mode,
            image = c.image,
            border = border,
            srcW = c.srcW,
            srcH = c.srcH,
            storedScale = c.storedScale,
            storedPanX = c.storedPanX,
            storedPanY = c.storedPanY,
        ),
    )
}
