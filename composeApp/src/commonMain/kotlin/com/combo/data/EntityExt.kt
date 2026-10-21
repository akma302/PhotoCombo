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
 * Вход для фона комбо: рамка → `avgColor` её фото, если метаданные фото есть в [photos].
 *
 * Фото, метаданные которых ещё не загружены, дают пустой список цветов: рамка остаётся в
 * списке (её площадь учитывается), а недогруженный цвет просто не участвует. Порядок
 * рамок — как у репозитория (по `zIndex`); на фон он не влияет.
 */
fun ComboWithItems.backgroundFrames(photos: Map<String, PhotoEntity>): List<Pair<Border, List<Int>>> =
    items.map { it.border() to listOfNotNull(photos[it.photoId]?.avgColor) }

/**
 * Содержимое рамки для [com.combo.core.render.buildItemFrame] — общий адаптер для превью
 * списка (данные приходят прямо из Room).
 *
 * Метаданные фото передаются отдельно, а не достаются из DAO: вызывающий уже держит
 * `photos[id]` в памяти, и повторный запрос здесь был бы лишним.
 */
fun ComboItemEntity.frameContent(image: ImageBitmap, photo: PhotoEntity) = FrameContent(
    image = image,
    srcW = photo.width,
    srcH = photo.height,
    avgColor = photo.avgColor,
    storedScale = contentScale,
    storedPanX = panX,
    storedPanY = panY,
)
