package com.combo.data

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.render.Border
import com.combo.core.render.FrameContent

/**
 * Раскладка полей Room-сущностей в термины ядра отрисовки.
 *
 * Почему это здесь, а не в `core`: единственное, что требуется от «сырых» данных, —
 * разложить их на `Border`/`Size`/цвета. Само преобразование значений (масштаб, pan,
 * режим рамки) делает `core/render` — там и живут правила.
 */

/** Рамка сущности в координатах холста. */
fun ComboItemEntity.border(): Border = Border(centerX, centerY, width, height)

/** Размер фото как `Size`: единственное место, где `Int`-пиксели становятся `Float`. */
fun PhotoEntity.srcSize(): Size = Size(width.toFloat(), height.toFloat())

/**
 * Вход для фона комбо: рамка → `avgColor` **всех** её фото, найденных в [photos].
 *
 * Фото, метаданные которых ещё не загружены, пропускаются: иначе одно недогруженное
 * фото обнулило бы вклад всей рамки. Порядок рамок — как у репозитория (по `zIndex`); на
 * фон он не влияет.
 */
fun ComboWithItems.backgroundFrames(photos: Map<String, PhotoEntity>): List<Pair<Border, List<Int>>> =
    items.map { iw ->
        iw.item.border() to iw.contents.mapNotNull { photos[it.photoId]?.avgColor }
    }

/**
 * Содержимое контента рамки для [com.combo.core.render.buildItemFrame] — общий адаптер
 * для плеера и превью списка (у них данные приходят прямо из Room).
 *
 * Метаданные фото передаются отдельно, а не достаются из DAO: вызывающий уже держит
 * `photos[id]` в памяти, и повторный запрос здесь был бы лишним.
 */
fun ComboItemContentEntity.frameContent(image: ImageBitmap, photo: PhotoEntity) = FrameContent(
    image = image,
    srcW = photo.width,
    srcH = photo.height,
    avgColor = photo.avgColor,
    storedScale = contentScale,
    storedPanX = panX,
    storedPanY = panY,
)