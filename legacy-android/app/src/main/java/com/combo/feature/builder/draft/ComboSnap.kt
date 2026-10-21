package com.combo.feature.builder.draft

import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.core.model.SwitchMode
import com.combo.core.model.TransitionType

/**
 * Снапшот одного фото внутри рамки: те же шесть полей, что у
 * [com.combo.core.render.ContentDraft], но значения, а не ссылки на мутабельный драфт.
 */
internal data class ContentSnap(
    val id: String, val photoId: String, val orderIndex: Int,
    val contentScale: Float, val panX: Float, val panY: Float,
)

/**
 * Снапшот рамки целиком: граница, режим показа и список фото.
 *
 * Набор полей обязан совпадать с набором полей [com.combo.core.render.ItemDraft] — иначе
 * добавленное в драфт поле молча выпало бы из undo, и Undo «отменил» бы не всё. Соответствие
 * наборов, а не только количества, проверяет `DraftMapperTest`.
 */
internal data class ItemSnap(
    val id: String, val zIndex: Int,
    val centerX: Float, val centerY: Float, val width: Float, val height: Float,
    val rotation: Float, val switchMode: SwitchMode, val autoIntervalMs: Long,
    val manualTransition: TransitionType, val autoTransition: TransitionType,
    val framingMode: FramingMode,
    val contents: List<ContentSnap>,
)

/**
 * Снапшот всего холста для undo: размеры, фон и упорядоченный список рамок. Имя комбо и
 * состояние редактора (зум/панорама вида, выделение) сюда НЕ входят — это не холст.
 *
 * ВАЖНО: все три класса — обычные data-классы, и их структурное равенство работает как
 * единственный индикатор «жест что-то изменил»:
 *
 * ```
 * if (pending != null && snapshot() != pending) { … }
 * ```
 *
 * Жест, который ничего не изменил (два пальца без движения, упёршийся в лимит размера),
 * обязан сравниться равным и не оставить пустого undo-шага. Что из этого следует и что
 * ломать нельзя:
 *
 * - не превращать снапшоты в обычные классы и не выносить часть полей в отдельный объект
 *   «не участвует в equals»;
 * - не добавлять сюда то, что меняется помимо действия пользователя (тики анимации
 *   переключения, счётчик, viewport): иначе пустой шаг перестанет отфильтровываться;
 * - не добавлять поле в драфт, не добавив его сюда (см. KDoc [ItemSnap]).
 */
internal data class ComboSnap(
    val canvasW: Float,
    val canvasH: Float,
    val background: BackgroundSpec,
    val items: List<ItemSnap>,
)
