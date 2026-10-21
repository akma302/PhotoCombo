package com.combo.core.playback

import android.os.SystemClock
import com.combo.core.model.Direction
import com.combo.core.model.TransitionType

/**
 * Состояние переключения фото в рамках: «текущий» индекс и идущий переход.
 *
 * Текущий индекс меняется СРАЗУ при [switch], а переход — только визуальный эффект, который
 * по времени `progress` догоняет уже произошедшую смену. Время берёт [now] (в тестах
 * подменяется).
 */
class SwitchAnimator(private val now: () -> Long = SystemClock::elapsedRealtime) {
    /**
     * Идущий переход: с какого фото на какое, каким эффектом, в каком направлении и когда начался.
     */
    data class Active(val from: Int, val to: Int, val type: TransitionType, val dir: Direction, val startedAt: Long)

    private val current = mutableMapOf<String, Int>()
    private val active = mutableMapOf<String, Active>()

    /** Текущее фото рамки; для неизвестной рамки — 0. */
    fun currentIndex(item: String): Int = current[item] ?: 0

    /**
     * Переключить рамку на фото [to].
     *
     * Индекс обновляется сразу. Переход запускается, только если `type.durationMs > 0`; смена на то
     * же фото ничего не делает.
     */
    fun switch(item: String, to: Int, type: TransitionType, dir: Direction) {
        val from = currentIndex(item)
        if (from == to) return
        current[item] = to
        if (type.durationMs == 0L) { active.remove(item); return }
        active[item] = Active(from, to, type, dir, now())
    }

    /**
     * Идущий переход рамки и его прогресс 0..1 (со сглаживанием) или `null`, если перехода нет.
     */
    fun frame(item: String): Pair<Active, Float>? = active[item]?.let { it to progress(it) }

    /** Оборвать переход рамки, не меняя текущий индекс. */
    fun cancel(item: String) {
        active.remove(item)
    }

    /**
     * Назначить «текущий» индекс без перехода.
     *
     * Нужен, когда индекс меняется не через [switch]: после удаления фото или рамки и после
     * отмены/повтора правки. [cancel] убирает только переход, а `current` остаётся прежним,
     * и следующий свайп стартовал бы из устаревшего индекса: `from` указывал бы на
     * удалённое фото (кадр без перехода) или совпал бы с `to` (тогда [switch] ничего не делает).
     */
    fun setCurrent(item: String, index: Int) {
        current[item] = index
    }

    /** Убирает завершённые переходы и возвращает рамки, у которых переход закончился. */
    fun finished(): List<String> =
        active.filter { progress(it.value) >= 1f }.keys.toList().also { it.forEach(active::remove) }

    /** Идёт ли хотя бы один переход. */
    fun hasActive(): Boolean = active.isNotEmpty()

    /** Прогресс перехода 0..1 с плавным разгоном и торможением (smoothstep). */
    private fun progress(a: Active): Float {
        val raw = ((now() - a.startedAt).toFloat() / a.type.durationMs).coerceIn(0f, 1f)
        return raw * raw * (3 - 2 * raw)
    }
}
