package com.combo

import com.combo.core.util.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    @Test
    fun `checkpoint enables undo then redo`() {
        val stack = UndoStack<Int>()
        assertFalse(stack.canUndo)
        stack.checkpoint(1)
        assertTrue(stack.canUndo)
        assertEquals(1, stack.undo(2))
        assertTrue(stack.canRedo)
        assertEquals(2, stack.redo(3))
        assertFalse(stack.canRedo)
    }

    @Test
    fun `empty stack returns null`() {
        val stack = UndoStack<String>()
        assertNull(stack.undo("x"))
        assertNull(stack.redo("x"))
    }

    @Test
    fun `max depth evicts oldest`() {
        val stack = UndoStack<Int>(maxDepth = 3)
        (1..5).forEach { stack.checkpoint(it) }
        assertEquals(5, stack.undo(9))
        assertEquals(4, stack.undo(8))
        assertEquals(3, stack.undo(7))
        assertNull(stack.undo(6))
    }

    @Test
    fun `checkpoint clears redo`() {
        val stack = UndoStack<Int>()
        stack.checkpoint(1)
        stack.undo(2)
        assertTrue(stack.canRedo)
        stack.checkpoint(10)
        assertFalse(stack.canRedo)
    }
}
