package com.combo.feature.builder

import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.core.model.SwitchMode
import com.combo.core.model.TransitionType
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.ContentSnap
import com.combo.feature.builder.draft.ItemSnap
import com.combo.feature.builder.history.DraftHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * История правок редактора ([DraftHistory]): фазы жеста, undo и redo без ViewModel.
 *
 * Проверяется главное: «оставил ли жест undo-шаг». Для этого не нужны ни Room-заглушки,
 * ни корутины: [DraftHistory] видит состояние редактора только через две лямбды
 * (снять снимок, восстановить снимок), а тест считает, сколько раз они вызваны.
 *
 * Индикатором «жест что-то изменил» служит СТРУКТУРНОЕ РАВЕНСТВО [ComboSnap]. Поэтому
 * тесты меняют разные уровни снимка (размер холста, поле рамки, поле фото в рамке): если
 * бы сравнение шло только по одному из них, остальные случаи проходили бы вхолостую.
 */
class DraftHistoryTest {

    /** Снимок рамки: меняется только [centerX], остальные поля обязаны остаться равными. */
    private fun itemSnapOf(
        id: String,
        centerX: Float,
        contents: List<ContentSnap> = emptyList(),
    ) = ItemSnap(
        id = id,
        zIndex = 0,
        centerX = centerX,
        centerY = 450f,
        width = 800f,
        height = 450f,
        rotation = 0f,
        switchMode = SwitchMode.MANUAL,
        autoIntervalMs = 3000L,
        manualTransition = TransitionType.SLIDE,
        autoTransition = TransitionType.FADE,
        framingMode = FramingMode.FREE,
        contents = contents,
    )

    private fun snapOf(
        canvasW: Float = 1600f,
        centerX: Float = 800f,
        contents: List<ContentSnap> = emptyList(),
    ) = ComboSnap(
        canvasW = canvasW,
        canvasH = 900f,
        background = BackgroundSpec.Auto(),
        items = listOf(itemSnapOf("i1", centerX, contents)),
    )

    private fun contentSnapOf(panX: Float) = ContentSnap(
        id = "c1",
        photoId = "p1",
        orderIndex = 0,
        contentScale = 1f,
        panX = panX,
        panY = 0f,
    )

    private val start: ComboSnap = snapOf()

    /** Состояние «экрана»: [DraftHistory] видит его только через лямбды. */
    private var current: ComboSnap = start
    private var restoreCalls = 0

    private val history = DraftHistory(
        snapshot = { current },
        restore = { snap ->
            current = snap
            restoreCalls++
        },
    )

    // ---------- START / CHANGE / END ----------

    @Test
    fun `START CHANGE END puts exactly one undo step`() {
        history.begin()
        current = snapOf(canvasW = 1200f)
        assertTrue("END обязан сообщить, что снимок изменился", history.end())
        assertTrue(history.canUndo)
        assertFalse("redo появляется только после undo", history.canRedo)
        assertEquals("END не откатывает — это не CANCEL", 0, restoreCalls)
    }

    @Test
    fun `END without changes leaves no undo step`() {
        history.begin()

        assertFalse(history.end())

        assertFalse("жест без изменений не должен оставлять пустой шаг", history.canUndo)
        assertFalse(history.canRedo)
    }

    /**
     * Проверяется глубина истории, а не только флаг: после пустого END отменяться должен
     * ПЕРВЫЙ шаг, а не мнимо новый. Иначе первый Undo ничего бы не отменял.
     */
    @Test
    fun `empty gesture does not deepen the history`() {
        history.checkpoint()
        current = snapOf(centerX = 810f)
        history.begin()

        assertFalse(history.end())
        assertTrue(history.canUndo)

        history.undo()

        assertEquals("отмениться должен первый шаг, а не пустой второй", start, current)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    /**
     * Изменение поля рамки (а не размера холста) тоже считается изменением: проверка
     * «сравни только размер холста» прошла бы здесь незамеченной.
     */
    @Test
    fun `a change of an item field counts as a change`() {
        history.begin()
        current = snapOf(centerX = 900f)

        assertTrue(history.end())
        assertTrue(history.canUndo)
    }

    /** То же для самого глубокого уровня снимка: поле фото внутри рамки. */
    @Test
    fun `a change inside a photo of an item counts as a change`() {
        current = snapOf(contents = listOf(contentSnapOf(panX = 0f)))
        history.begin()
        current = snapOf(contents = listOf(contentSnapOf(panX = 15f)))

        assertTrue(history.end())
        assertTrue(history.canUndo)
    }

    @Test
    fun `END without START puts no step`() {
        current = snapOf(centerX = 810f)

        assertFalse(history.end())
        assertFalse(history.canUndo)
    }

    /** Пустой жест после настоящего не трогает уже поставленный undo-шаг. */
    @Test
    fun `an empty END keeps the undo step of an earlier gesture`() {
        history.begin()
        current = snapOf(centerX = 810f)
        assertTrue(history.end())
        assertTrue(history.canUndo)

        history.begin()
        assertFalse(history.end())
        assertTrue("пустой END не должен откатывать уже поставленный шаг", history.canUndo)
    }

    // ---------- CANCEL ----------

    @Test
    fun `CANCEL restores the START snapshot and leaves no undo step`() {
        history.begin()
        current = snapOf(centerX = 810f)

        history.cancel()

        assertEquals("CANCEL обязан вернуть состояние на START", start, current)
        assertEquals(1, restoreCalls)
        assertFalse("откаченный жест не должен оставаться в истории", history.canUndo)
    }

    /** Без START откатывать нечего, и пересобирать драфты незачем. */
    @Test
    fun `CANCEL without START restores nothing`() {
        history.cancel()

        assertEquals(0, restoreCalls)
        assertEquals(start, current)
    }

    // ---------- undo / redo ----------

    @Test
    fun `undo and redo walk the history and flip the flags`() {
        val first = snapOf(centerX = 810f)
        val second = snapOf(centerX = 820f)
        history.checkpoint()
        current = first
        history.checkpoint()
        current = second

        history.undo()
        assertEquals(first, current)
        assertTrue(history.canUndo)
        // После undo redo ОБЯЗАН стать доступным: на него кладётся текущий снимок.
        assertTrue(history.canRedo)

        history.undo()
        assertEquals(start, current)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        history.redo()
        assertEquals(first, current)
        assertTrue(history.canUndo)
        assertTrue(history.canRedo)

        history.redo()
        assertEquals(second, current)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo and redo on an empty history do nothing`() {
        history.undo()
        history.redo()

        assertEquals("пустая история не должна дёргать восстановление", 0, restoreCalls)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    // ---------- checkpoint ----------

    /**
     * Главное свойство checkpoint'а: он закрывает начатый жест. Иначе прерванный жест
     * откатил бы к снимку, взятому ДО действия кнопки, и отменил бы чужую правку.
     */
    @Test
    fun `checkpoint drops the pending gesture snapshot`() {
        history.begin()
        history.checkpoint()
        current = snapOf(centerX = 810f)

        history.cancel()

        assertEquals(
            "pending сброшен checkpoint'ом — откатываться не на что",
            snapOf(centerX = 810f),
            current,
        )
        assertEquals(0, restoreCalls)
        assertTrue(history.canUndo)
    }

    /** START после START берёт «до» от текущего состояния, а не от первого START. */
    @Test
    fun `a second START overwrites the pending snapshot`() {
        history.begin()
        current = snapOf(centerX = 810f)
        history.begin()
        current = snapOf(centerX = 820f)

        history.cancel()

        assertEquals("откат обязан идти к снимку второго START", snapOf(centerX = 810f), current)
    }
}