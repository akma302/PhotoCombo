package com.combo.feature.builder.draft

import com.combo.core.render.ContentDraft
import com.combo.core.render.FrameContent
import com.combo.core.render.ItemDraft
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity

/**
 * Загрузка комбо из базы в драфты редактора.
 *
 * Три неочевидных места — всё это поведение, а не деталь реализации:
 *
 * - рамки сортируются по `zIndex` и `zIndex` тут же переприсваивается подряд (`0..n-1`):
 *   после удаления/перестановки рамок в базе остаются дыры, и хранить их незачем — то же
 *   «перенумеровать подряд» делает и запись в базу, так что порядок слоёв после
 *   загрузки/сохранения/перезагрузки не плывёт;
 * - содержимое сортируется по `orderIndex`: `@Relation` не сортирует, а по `zIndex` мы уже
 *   отсортировали рамки целиком;
 * - `contentScale`/`panX`/`panY` копируются как есть, без `LayoutPresets.fill`: сохранённые
 *   значения — источник истины, в том числе для рамки в режиме FIT, где эффективное
 *   вписывание вычисляется при отрисовке. Перезаписывать их значило бы «сдвинуть» фото при
 *   каждом открытии комбо.
 */
internal fun ComboWithItems.toDrafts(): List<ItemDraft> =
    items.sortedBy { it.item.zIndex }.mapIndexed { i, iw ->
        ItemDraft(
            id = iw.item.id,
            zIndex = i,
            centerX = iw.item.centerX,
            centerY = iw.item.centerY,
            width = iw.item.width,
            height = iw.item.height,
            rotation = iw.item.rotation,
            switchMode = iw.item.switchModeEnum,
            autoIntervalMs = iw.item.autoIntervalMs,
            manualTransition = iw.item.manualTransitionEnum,
            autoTransition = iw.item.autoTransitionEnum,
            framingMode = iw.item.framingModeEnum,
            contents = iw.contents.sortedBy { it.orderIndex }.map { c ->
                ContentDraft(c.id, c.photoId, c.orderIndex, c.contentScale, c.panX, c.panY)
            }.toMutableList(),
        )
    }

/**
 * Сборка сущностей для сохранения: первым элементом пары — рамки, вторым — фото.
 *
 * `zIndex` берётся НЕ из драфта, а из позиции в списке: пустые рамки убирает вызывающий
 * код до вызова, и после этого номера обязаны идти подряд, иначе в базе снова появятся
 * дыры в порядке слоёв (см. KDoc [toDrafts]).
 *
 * Enum'ы пишутся через `.name`: соответствующие колонки в `ComboItemEntity` строковые.
 */
internal fun List<ItemDraft>.toEntities(
    comboId: String,
): Pair<List<ComboItemEntity>, List<ComboItemContentEntity>> {
    val items = mapIndexed { i, d ->
        ComboItemEntity(
            id = d.id, comboId = comboId, zIndex = i,
            centerX = d.centerX, centerY = d.centerY,
            width = d.width, height = d.height,
            rotation = d.rotation,
            switchMode = d.switchMode.name,
            autoIntervalMs = d.autoIntervalMs,
            manualTransition = d.manualTransition.name,
            autoTransition = d.autoTransition.name,
            framingMode = d.framingMode.name,
        )
    }
    val contents = flatMap { d ->
        d.contents.map { c ->
            ComboItemContentEntity(
                id = c.id, itemId = d.id, photoId = c.photoId,
                orderIndex = c.orderIndex, contentScale = c.contentScale,
                panX = c.panX, panY = c.panY,
            )
        }
    }
    return items to contents
}

/**
 * Снапшот драфта для undo. Глубокое копирование по полям обязательно: содержимое мутируется
 * на месте жеста, и снапшот должен остаться тем, чем был на START.
 */
internal fun ItemDraft.toSnap(): ItemSnap = ItemSnap(
    id = id, zIndex = zIndex,
    centerX = centerX, centerY = centerY,
    width = width, height = height,
    rotation = rotation,
    switchMode = switchMode,
    autoIntervalMs = autoIntervalMs,
    manualTransition = manualTransition,
    autoTransition = autoTransition,
    framingMode = framingMode,
    contents = contents.map { c ->
        ContentSnap(c.id, c.photoId, c.orderIndex, c.contentScale, c.panX, c.panY)
    },
)

/**
 * Обратное преобразование: новые мутабельные драфты из снапшота (в том числе `contents` —
 * снова `MutableList`, чтобы жесты могли менять рамку дальше).
 */
internal fun ItemSnap.toDraft(): ItemDraft = ItemDraft(
    id = id, zIndex = zIndex,
    centerX = centerX, centerY = centerY,
    width = width, height = height,
    rotation = rotation,
    switchMode = switchMode,
    autoIntervalMs = autoIntervalMs,
    manualTransition = manualTransition,
    autoTransition = autoTransition,
    framingMode = framingMode,
    contents = contents.map { c ->
        ContentDraft(c.id, c.photoId, c.orderIndex, c.contentScale, c.panX, c.panY)
    }.toMutableList(),
)

/**
 * Содержимое драфта для [com.combo.core.render.buildItemFrame].
 *
 * Аналог `com.combo.data.frameContent` для сущностей Room: своя версия нужна потому,
 * что у билдера данные лежат в мутабельных драфтах, а не в Room-сущностях. Держим рядом
 * с остальным маппером, чтобы список полей драфта читался в одном файле.
 */
internal fun ContentDraft.frameContent(image: androidx.compose.ui.graphics.ImageBitmap, photo: PhotoEntity) =
    FrameContent(
        image = image,
        srcW = photo.width,
        srcH = photo.height,
        avgColor = photo.avgColor,
        storedScale = contentScale,
        storedPanX = panX,
        storedPanY = panY,
    )