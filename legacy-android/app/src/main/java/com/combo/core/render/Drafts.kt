package com.combo.core.render

import com.combo.Const
import com.combo.core.model.SwitchMode
import com.combo.core.model.FramingMode
import com.combo.core.model.TransitionType

/**
 * Одно фото внутри рамки в редакторе. Изменяемый драфт: жесты меняют поля на месте.
 *
 * @property contentScale масштаб фото относительно исходного размера;
 * @property panX сдвиг фото от центра рамки по X, в координатах холста; [panY] — по Y.
 * Для рамки в режиме FIT эти значения не показываются (см. [effectiveFraming]).
 */
class ContentDraft(
    val id: String,
    val photoId: String,
    var orderIndex: Int,
    var contentScale: Float,
    var panX: Float,
    var panY: Float,
)

/**
 * Рамка в редакторе вместе с её фото. Изменяемый драфт по замыслу: жесты меняют поля на
 * месте, чтобы не копировать состояние на каждом кадре; для undo используются снимки
 * ([com.combo.feature.builder.draft.ComboSnap]).
 */
class ItemDraft(
    val id: String,
    var zIndex: Int,
    var centerX: Float,
    var centerY: Float,
    var width: Float,
    var height: Float,
    var rotation: Float = 0f,
    var switchMode: SwitchMode = SwitchMode.MANUAL,
    var autoIntervalMs: Long = Const.DEFAULT_AUTO_INTERVAL_MS,
    var manualTransition: TransitionType = TransitionType.SLIDE,
    var autoTransition: TransitionType = TransitionType.FADE,
    /** Режим рамки: FREE — хранимые значения значимы, FIT — они вычисляются при отрисовке. */
    var framingMode: FramingMode = FramingMode.FREE,
    val contents: MutableList<ContentDraft>,
) {
    fun border() = Border(centerX, centerY, width, height)
}
