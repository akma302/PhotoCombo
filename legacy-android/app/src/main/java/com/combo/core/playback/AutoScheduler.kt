package com.combo.core.playback

import android.os.SystemClock

/**
 * Расписание автосмены фото: для каждой рамки хранится момент следующего срабатывания.
 *
 * Время берёт функция [now] (в тестах подменяется). Пауза запоминает ОСТАТОК интервала,
 * а `resume` продолжает с него, поэтому свёрнутое приложение не «копит» сработавшие таймеры.
 */
class AutoScheduler(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private class Slot(var nextAt: Long, val interval: Long) { var remaining: Long? = null }
    private val slots = mutableMapOf<String, Slot>()

    /** Запустить таймер рамки [item]: первое срабатывание через [interval] мс. */
    fun schedule(item: String, interval: Long) { slots[item] = Slot(now() + interval, interval) }

    /** Остановить таймер рамки. */
    fun cancel(item: String) { slots.remove(item) }

    /** Поставить все таймеры на паузу, запомнив, сколько осталось до срабатывания. */
    fun pause() = slots.values.forEach {
        if (it.remaining == null) it.remaining = (it.nextAt - now()).coerceAtLeast(0)
    }

    /** Снять паузу: каждый таймер дорабатывает свой запомненный остаток. */
    fun resume() = slots.values.forEach {
        it.remaining?.let { r -> it.nextAt = now() + r; it.remaining = null }
    }

    /** Рамки, у которых пора менять фото (таймер не на паузе и уже сработал). */
    fun due(): List<String> =
        slots.filterValues { it.remaining == null && it.nextAt <= now() }.keys.toList()

    /**
     * Отсчитать интервал заново: вызывается после ручной смены фото, чтобы автосмена не
     * сработала сразу вслед за ней.
     */
    fun reschedule(item: String) { slots[item]?.let { it.nextAt = now() + it.interval } }

    /**
     * Есть ли таймеры не на паузе. Пока они есть, тикер обязан жить, даже если прямо сейчас
     * ничего не «пора»: иначе он остановился бы до первого срабатывания.
     */
    fun hasRunning(): Boolean = slots.values.any { it.remaining == null }

    /**
     * Через сколько мс сработает ближайший работающий таймер (0 — уже пора) или `null`, если
     * работающих таймеров нет и тикеру нечего ждать.
     */
    fun nextDelayMs(): Long? {
        val now = now()
        var best: Long? = null
        for (slot in slots.values) {
            if (slot.remaining != null) continue
            val delta = (slot.nextAt - now).coerceAtLeast(0L)
            if (best == null || delta < best) best = delta
        }
        return best
    }
}
