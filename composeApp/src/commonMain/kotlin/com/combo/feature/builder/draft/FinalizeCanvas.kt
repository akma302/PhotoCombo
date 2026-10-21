package com.combo.feature.builder.draft

import kotlin.math.ceil

/**
 * Финальный холст охватывает все рамки целиком, включая выступающие за рабочий холст.
 * Перенос к новому началу координат сохраняет размеры рамок, слои и положение фото.
 * Снимок независим от мутабельного редактора: ошибка записи не должна обрезать драфт.
 * Размеры округляются вверх до целого пикселя, чтобы не отрезать дробный край рамки.
 */
internal fun ComboSnap.trimmedToFrames(): ComboSnap {
    if (items.isEmpty()) return this
    val left = items.minOf { it.centerX - it.width / 2f }
    val top = items.minOf { it.centerY - it.height / 2f }
    val right = items.maxOf { it.centerX + it.width / 2f }
    val bottom = items.maxOf { it.centerY + it.height / 2f }
    return copy(
        canvasW = ceil(right - left).coerceAtLeast(1f),
        canvasH = ceil(bottom - top).coerceAtLeast(1f),
        items = items.map { it.copy(centerX = it.centerX - left, centerY = it.centerY - top) },
    )
}
