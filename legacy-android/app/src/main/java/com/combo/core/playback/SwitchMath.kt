package com.combo.core.playback

import com.combo.core.model.Direction

/**
 * Индекс фото после смены в направлении [dir] по кругу: вверх — следующее, вниз — предыдущее.
 *
 * Возвращает `null`, когда менять нечего: в рамке меньше двух фото или направление
 * горизонтальное (оно фото не переключает). Вызывающий в этом случае просто ничего не делает.
 */
fun cycleIndex(cur: Int, size: Int, dir: Direction): Int? {
    if (size < 2) return null
    return when (dir) {
        Direction.UP -> (cur + 1) % size
        Direction.DOWN -> (cur - 1 + size) % size
        // Горизонтальные направления переключением фото не являются.
        Direction.LEFT, Direction.RIGHT -> null
    }
}

/**
 * Следующее по кругу фото — для автосмены по таймеру и предзагрузки соседнего фото.
 * Для пустой рамки возвращает `null` (деление на ноль недопустимо).
 */
fun nextIndex(cur: Int, size: Int): Int? =
    if (size < 1) null else (cur + 1) % size
