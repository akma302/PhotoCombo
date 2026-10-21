package com.combo.feature.builder.draft

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.render.FrameContent
import com.combo.core.render.ItemDraft
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity

/**
 * Загрузка комбо из базы в драфты редактора.
 *
 * Два неочевидных места — всё это поведение, а не деталь реализации:
 *
 * - рамки сортируются по `zIndex` и `zIndex` тут же переприсваивается подряд (`0..n-1`):
 *   после удаления/перестановки рамок в базе остаются дыры, и хранить их незачем — то же
 *   «перенумеровать подряд» делает и запись в базу, так что порядок слоёв после
 *   загрузки/сохранения/перезагрузки не плывёт;
 * - `contentScale`/`panX`/`panY` копируются как есть, без `LayoutPresets.fill`: сохранённые
 *   значения — источник истины, в том числе для рамки в режиме FIT, где эффективное
 *   вписывание вычисляется при отрисовке. Перезаписывать их значило бы «сдвинуть» фото при
 *   каждом открытии комбо.
 */
internal fun ComboWithItems.toDrafts(): List<ItemDraft> =
    items.sortedBy { it.zIndex }.mapIndexed { i, e ->
        ItemDraft(
            id = e.id,
            zIndex = i,
            centerX = e.centerX,
            centerY = e.centerY,
            width = e.width,
            height = e.height,
            rotation = e.rotation,
            framingMode = e.framingModeEnum,
            photoId = e.photoId,
            contentScale = e.contentScale,
            panX = e.panX,
            panY = e.panY,
        )
    }

/**
 * Сборка сущностей для сохранения.
 *
 * `zIndex` берётся НЕ из драфта, а из позиции в списке: после этого номера обязаны идти
 * подряд, иначе в базе снова появятся дыры в порядке слоёв (см. KDoc [toDrafts]).
 *
 * Enum'ы пишутся через `.name`: соответствующие колонки в `ComboItemEntity` строковые.
 */
internal fun List<ItemDraft>.toEntities(comboId: String): List<ComboItemEntity> =
    mapIndexed { i, d ->
        ComboItemEntity(
            id = d.id, comboId = comboId, zIndex = i,
            centerX = d.centerX, centerY = d.centerY,
            width = d.width, height = d.height,
            rotation = d.rotation,
            framingMode = d.framingMode.name,
            photoId = d.photoId,
            contentScale = d.contentScale,
            panX = d.panX, panY = d.panY,
        )
    }

/**
 * Снапшот драфта для undo. Копирование по полям обязательно: драфт мутируется на месте
 * жеста, и снапшот должен остаться тем, чем был на START.
 */
internal fun ItemDraft.toSnap(): ItemSnap = ItemSnap(
    id = id, zIndex = zIndex,
    centerX = centerX, centerY = centerY,
    width = width, height = height,
    rotation = rotation,
    framingMode = framingMode,
    photoId = photoId,
    contentScale = contentScale, panX = panX, panY = panY,
)

/** Обратное преобразование: новый мутабельный драфт из снапшота. */
internal fun ItemSnap.toDraft(): ItemDraft = ItemDraft(
    id = id, zIndex = zIndex,
    centerX = centerX, centerY = centerY,
    width = width, height = height,
    rotation = rotation,
    framingMode = framingMode,
    photoId = photoId,
    contentScale = contentScale, panX = panX, panY = panY,
)

/**
 * Содержимое драфта для [com.combo.core.render.buildItemFrame].
 *
 * Аналог `com.combo.data.frameContent` для сущностей Room: своя версия нужна потому,
 * что у билдера данные лежат в мутабельных драфтах, а не в Room-сущностях. Держим рядом
 * с остальным маппером, чтобы список полей драфта читался в одном файле.
 */
internal fun ItemDraft.frameContent(image: ImageBitmap, photo: PhotoEntity) =
    FrameContent(
        image = image,
        srcW = photo.width,
        srcH = photo.height,
        avgColor = photo.avgColor,
        storedScale = contentScale,
        storedPanX = panX,
        storedPanY = panY,
    )
