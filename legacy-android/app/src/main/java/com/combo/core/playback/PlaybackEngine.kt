package com.combo.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Тикер воспроизведения: общий для редактора и плеера.
 *
 * Крутит анимации переходов (кадр раз в 16 мс) и срабатывания автосмены по расписанию; когда
 * нечего анимировать, спит до ближайшего таймера. Всё, чем потребители различаются, передаётся
 * тремя колбэками:
 *  - [isAlive] — пока возвращает `true`, цикл продолжается. Плеер держит его, пока загружено
 *    комбо и есть анимация или работающий таймер; редактор — пока идёт анимация;
 *  - [onDue] — вызывается для каждой рамки, у которой пора менять фото (смена и перезапуск
 *    таймера — на стороне вызывающего);
 *  - [onFrame] — просит перерисовать экран (`revision++`).
 *
 * Редактор работает без расписания: пустой [AutoScheduler] не создаёт таймеров, [onDue] не
 * вызывается, и цикл живёт ровно пока идёт анимация.
 *
 * Корутина запускается в [scope] (у обоих экранов это `viewModelScope`), отдельная остановка
 * не нужна: отмена scope останавливает и тикер.
 */
class PlaybackEngine(
    private val scope: CoroutineScope,
    val animator: SwitchAnimator,
    val scheduler: AutoScheduler,
    private val isAlive: () -> Boolean,
    private val onDue: (itemId: String) -> Unit,
    private val onFrame: () -> Unit,
) {

    private var job: Job? = null

    /**
     * Сигнал «проснись». Тикер спит до ближайшего таймера, а ручной свайп или `resume`
     * должны отрисоваться сразу, а не через несколько секунд. Канал CONFLATED: накопленные
     * сигналы сливаются в один, лишняя итерация цикла безвредна.
     */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Запускает тикер, если он не запущен, а если запущен — будит: живой тикер может спать
     * до ближайшего таймера, и «запущен» не значит «не спит».
     */
    fun startIfNeeded() {
        if (job?.isActive == true) {
            wake.trySend(Unit)
            return
        }
        job = scope.launch { runLoop() }
    }

    /** Будит тикер, не проверяя, запущен ли он: следующая итерация начнётся немедленно. */
    fun wake() {
        wake.trySend(Unit)
    }

    /**
     * Одна итерация: закрыть завершённые переходы, обработать сработавшие таймеры,
     * перерисовать, если что-то изменилось, и уснуть. Порядок шагов важен для таймингов;
     * в тестах на виртуальном времени он проверяется.
     */
    private suspend fun runLoop() {
        while (true) {
            animator.finished()
            val due = scheduler.due()
            if (!isAlive()) break

            if (due.isNotEmpty()) {
                due.forEach { itemId -> onDue(itemId) }
            }
            // Без анимации и без сработавших таймеров рисовать нечего: безусловная
            // перерисовка гоняла бы холст 60 раз в секунду впустую.
            if (animator.hasActive() || due.isNotEmpty()) onFrame()

            // Пока идёт анимация — тикаем с частотой кадров, иначе спим до ближайшего
            // таймера. Сон прерывается сигналом в `wake`.
            val animating = animator.hasActive()
            val nextDelay = scheduler.nextDelayMs()
            if (!animating && nextDelay == null) break
            val sleepMs = if (animating) 16L else nextDelay!!.coerceAtLeast(1L)
            withTimeoutOrNull(sleepMs.milliseconds) { wake.receive() }
        }
        onFrame()
    }
}