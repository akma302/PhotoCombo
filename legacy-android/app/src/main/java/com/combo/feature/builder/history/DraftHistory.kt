package com.combo.feature.builder.history

import com.combo.core.util.UndoStack
import com.combo.feature.builder.draft.ComboSnap

/**
 * История правок холста редактора: стек undo плюс снимок незавершённого жеста.
 *
 * Состояние ровно два: [UndoStack] снимков и `pendingGestureSnap` — снимок, снятый на
 * START жеста. Всё остальное приходит лямбдами ([snapshot], [restore]), поэтому класс
 * ничего не знает ни про ViewModel, ни про состояние экрана, и фазы жеста проверяются
 * обычным JVM-тестом без Robolectric и без фейков репозиториев.
 *
 * Правила, которые здесь тестируются напрямую: снимок на START, фильтр пустого шага,
 * откат по CANCEL, сброс pending при checkpoint.
 *
 * ГРАНИЦА ОТВЕТСТВЕННОСТИ. Класс не знает про `isDirty` и `revision`: это состояние
 * экрана, а не история. Именно поэтому [end] возвращает `Boolean` — «снапшот изменился,
 * checkpoint поставлен»: вызывающий по этому признаку решает, помечать ли комбо
 * изменённым, и не обязан повторять сравнение снапшотов ещё раз.
 *
 * ИНВАРИАНТ [ComboSnap]. Определяющим является структурное равенство data-класса:
 * `snapshot() != pending` — единственный индикатор «жест что-то изменил». Поэтому в
 * [ComboSnap] нельзя добавлять то, что меняется помимо действия пользователя (тики
 * анимации переключения, счётчик, viewport) — пустой шаг перестанет отфильтровываться.
 */
internal class DraftHistory(
    private val snapshot: () -> ComboSnap,
    private val restore: (ComboSnap) -> Unit,
) {
    private val undoStack = UndoStack<ComboSnap>()

    /**
     * Снимок на START, ещё не записанный в undo-стек.
     *
     * Пока он здесь, откат CANCEL — это просто `restore(pending)`, поэтому промежуточные
     * кадры жеста (десятки `CHANGE` подряд) в истории не видно: шаг ровно один, и он
     * такой же, как у кнопки тулбара.
     */
    private var pendingGestureSnap: ComboSnap? = null

    /**
     * START: снимок ДО первой мутации.
     *
     * Перезаписывать (а не накапливать) правильно: если START пришёл без END — gesture
     * прервался, и следующий START всё равно должен считать «до» от текущего состояния,
     * иначе Undo откатил бы к моменту, который пользователь уже не видел.
     */
    fun begin() {
        pendingGestureSnap = snapshot()
    }

    /**
     * END: если снимок изменился — ставит checkpoint и возвращает `true`; жест, который
     * ничего не изменил, оставляет историю как есть и возвращает `false`.
     */
    fun end(): Boolean {
        val pending = pendingGestureSnap
        pendingGestureSnap = null
        // Жест, который ничего не изменил (два пальца без движения, упёршийся в лимит
        // размера), не должен оставлять пустой undo-шаг и помечать комбо изменённым.
        if (pending == null || snapshot() == pending) return false
        undoStack.checkpoint(pending)
        return true
    }

    /**
     * CANCEL: откат к снимку START.
     *
     * Восстанавливается ТОЛЬКО снятый на START снимок. Если START не было (жест не
     * начался, а пришёл сразу CANCEL), восстанавливать нечего — и это не ошибка: текущий
     * снимок уже актуален, а подставлять его вместо себя самого значило бы лишний
     * rebuild драфтов и потерю выделения.
     */
    fun cancel() {
        pendingGestureSnap?.let { restore(it) }
        pendingGestureSnap = null
    }

    /**
     * Checkpoint на завершённом действии, не на тике жеста: так ставят кнопки тулбара и
     * события, меняющие холст целиком.
     *
     * pending здесь сбрасывается обязательно. Иначе начатый, но не завершённый жест,
     * прерванный действием с панели (или следующим START), откатился бы к снимку, взятому
     * ДО этого действия, и отменил бы вместе с ним ещё и чужую правку.
     */
    fun checkpoint() {
        undoStack.checkpoint(snapshot())
        pendingGestureSnap = null
    }

    /**
     * Undo: на undo кладётся ТЕКУЩИЙ снимок (чтобы его можно было повторно положить в
     * redo), а восстанавливается снимок из стека. Пустой стек — не ошибка: событие
     * просто ничего не делает, флаги остаются прежними.
     */
    fun undo() {
        val snap = undoStack.undo(snapshot()) ?: return
        restore(snap)
    }

    /** Redo — зеркало [undo]. На redo кладётся текущий снимок, восстанавливается redo-ный. */
    fun redo() {
        val snap = undoStack.redo(snapshot()) ?: return
        restore(snap)
    }

    val canUndo: Boolean get() = undoStack.canUndo

    val canRedo: Boolean get() = undoStack.canRedo
}