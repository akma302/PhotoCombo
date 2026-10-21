package com.combo

import com.combo.core.playback.cycleIndex
import com.combo.core.playback.nextIndex
import com.combo.core.model.Direction
import com.combo.core.render.isVerticalFling
import com.combo.core.render.flingDirection
import androidx.compose.ui.unit.Velocity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Чистые помощники смены фото: циклическая арифметика индексов ([cycleIndex], [nextIndex])
 * и направление флинга ([flingDirection]).
 *
 * Эталоном служит прямое определение (`mod` по размеру набора, знак вертикальной
 * скорости), а не сама реализация: ошибка в помощнике показала бы не то фото.
 */
class SwitchMathTest {

    // ---------- cycleIndex ----------

    @Test
    fun `UP is the next photo and DOWN is the previous`() {
        assertEquals("UP из 0 при 3 фото", 1, cycleIndex(0, 3, Direction.UP))
        assertEquals("UP замыкается по кругу", 0, cycleIndex(2, 3, Direction.UP))
        assertEquals("DOWN из 0 берёт последнее", 2, cycleIndex(0, 3, Direction.DOWN))
        assertEquals("DOWN из 2 при 3 фото", 1, cycleIndex(2, 3, Direction.DOWN))
    }

    @Test
    fun `cycleIndex agrees with modular arithmetic for every size and position`() {
        for (size in 2..7) {
            for (cur in 0 until size) {
                assertEquals(
                    "UP(size=$size, cur=$cur)",
                    (cur + 1).mod(size),
                    cycleIndex(cur, size, Direction.UP),
                )
                assertEquals(
                    "DOWN(size=$size, cur=$cur)",
                    (cur - 1 + size).mod(size),
                    cycleIndex(cur, size, Direction.DOWN),
                )
            }
        }
    }

    @Test
    fun `cycleIndex returns null when there is nothing to switch between`() {
        // Меньше двух фото переключать не на что; для пустой рамки деление на ноль
        // (ArithmeticException) недопустимо.
        assertNull(cycleIndex(0, 0, Direction.UP))
        assertNull(cycleIndex(0, 1, Direction.UP))
        assertNull(cycleIndex(0, 1, Direction.DOWN))
    }

    @Test
    fun `cycleIndex ignores horizontal directions`() {
        // LEFT/RIGHT — это не «смена фото»: флинг по ним ничего не должен делать.
        assertNull(cycleIndex(1, 3, Direction.LEFT))
        assertNull(cycleIndex(1, 3, Direction.RIGHT))
    }

    // ---------- nextIndex ----------

    @Test
    fun `nextIndex is for autoplay and preload`() {
        assertEquals(1, nextIndex(0, 3))
        assertEquals(0, nextIndex(2, 3))
        assertEquals("при одном фото остаётся он же", 0, nextIndex(0, 1))
    }

    @Test
    fun `nextIndex returns null instead of dividing by zero`() {
        assertNull(nextIndex(0, 0))
        assertNull(nextIndex(5, 0))
    }

    // ---------- flingDirection ----------

    @Test
    fun `fast vertical fling maps to a direction`() {
        assertEquals(Direction.UP, flingDirection(Velocity(0f, -2000f)))
        assertEquals(Direction.DOWN, flingDirection(Velocity(0f, 2000f)))
    }

    @Test
    fun `slow or diagonal movement is not a fling`() {
        assertNull("медленно", flingDirection(Velocity(0f, 100f)))
        assertNull("диагональ", flingDirection(Velocity(1200f, 2000f)))
        assertNull("нулевая скорость", flingDirection(Velocity.Zero))
    }

    /** Направление определяется знаком вертикальной скорости: UP — палец идёт вверх. */
    @Test
    fun `flingDirection follows isVerticalFling and the sign of the vertical velocity`() {
        val cases = listOf(
            Velocity(0f, 1700f), Velocity(0f, -1700f), Velocity(300f, 1900f),
            Velocity(1500f, 1800f), Velocity(0f, 1600f), Velocity(0f, 1599f),
        )
        for (v in cases) {
            val expected = if (isVerticalFling(v.x, v.y)) {
                if (v.y < 0) Direction.UP else Direction.DOWN
            } else {
                null
            }
            assertEquals("v=$v", expected, flingDirection(v))
        }
    }
}
