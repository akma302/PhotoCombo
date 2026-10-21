package com.combo.core.render

import com.combo.core.model.FramingMode

/**
 * Рамка в редакторе вместе с её фото. В рамке ровно одно фото. Изменяемый драфт по
 * замыслу: жесты меняют поля на месте, чтобы не копировать состояние на каждом кадре; для
 * undo используются снимки ([com.combo.feature.builder.draft.ComboSnap]).
 *
 * @property photoId фото рамки;
 * @property contentScale масштаб фото относительно исходного размера;
 * @property panX сдвиг фото от центра рамки по X, в координатах холста; [panY] — по Y.
 * Для рамки в режиме FIT эти значения не показываются (см. [effectiveFraming]).
 * @property framingMode FREE — хранимые значения значимы, FIT — они вычисляются при
 * отрисовке.
 */
class ItemDraft(
    val id: String,
    var zIndex: Int,
    var centerX: Float,
    var centerY: Float,
    var width: Float,
    var height: Float,
    var rotation: Float = 0f,
    var framingMode: FramingMode = FramingMode.FREE,
    var photoId: String,
    var contentScale: Float,
    var panX: Float,
    var panY: Float,
) {
    fun border() = Border(centerX, centerY, width, height)
}
