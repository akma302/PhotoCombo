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
 * Callers assemble a frame after the photo is available.
 */
fun buildItemFrame(border: Border, mode: FramingMode, content: FrameContent): ItemFrame =
    ItemFrame(
        border = border,
        fillColor = Color(content.avgColor),
        content = contentArgsOf(
            mode = mode,
            image = content.image,
            border = border,
            srcW = content.srcW,
            srcH = content.srcH,
            storedScale = content.storedScale,
            storedPanX = content.storedPanX,
            storedPanY = content.storedPanY,
        ),
    )
