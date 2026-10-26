package com.combo.core.util

/**
 * Стек отмены и повтора с ограниченной глубиной (старые шаги вытесняются).
 *
 * `checkpoint` вызывают только после завершённого действия, а не на каждом шаге жеста.
 * Новое действие после отмены очищает «повтор».
 */
class UndoStack<T>(private val maxDepth: Int = 50) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()

    /** Запомнить состояние ДО действия; очищает стек повтора. */
    fun checkpoint(before: T) {
        undo.addLast(before)
        if (undo.size > maxDepth) undo.removeFirst()
        redo.clear()
    }

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Отменить: возвращает прошлое состояние (или `null`) и откладывает [current] для повтора. */
    fun undo(current: T): T? = undo.removeLastOrNull()?.also { redo.addLast(current) }
    /**
     * Повторить: возвращает отменённое состояние (или `null`) и откладывает [current] для отмены.
     */
    fun redo(current: T): T? = redo.removeLastOrNull()?.also { undo.addLast(current) }
}
