package com.combo

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Защита от повторения порчи исходников при правках «через PowerShell»: невалидный
 * UTF-8, U+FFFD (заменённый символ), CRLF, длинные цепочки вопросительных знаков и
 * съехавшие на колонку 0 строки внутри блоков — это следы перекодировки/правки не
 * редактором, а не «просто комментарий».
 *
 * Корень сборки передаётся Gradle; сканируются src всех модулей без build/generated.
 */
class SourceHygieneTest {

    private val srcDir = File(checkNotNull(System.getProperty("photocombo.root")))
    private val modules = checkNotNull(System.getProperty("photocombo.modules")).split('|')

    private val U_FFFD = 0xFFFD.toChar()

    /** Собираем иголку из кусков: иначе тест ловит сам себя — в его же тексте есть пример. */
    private val QMARK_RUN = Regex(Regex.escape("?") + "{4}")

    private fun kotlinFiles(): List<File> =
        modules.flatMap { module -> File(srcDir, "$module/src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.toList() }.asSequence()
            .sortedBy { it.path }
            .toList()

    @Test
    fun `kotlin sources are valid utf8 without bom, lf only and without damaged text`() {
        val problems = mutableListOf<String>()
        val strict = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        for (file in kotlinFiles()) {
            val bytes = file.readBytes()
            val rel = file.relativeTo(srcDir).path

            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) {
                problems += "$rel: UTF-8 BOM"
            }

            val text = try {
                strict.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (e: Exception) {
                problems += "$rel: невалидный UTF-8 (${e.javaClass.simpleName})"
                // Без корректного декодирования дальше смотреть нечего.
                continue
            }

            text.split('\n').forEachIndexed { i, line ->
                val n = i + 1
                if (line.contains('\r')) problems += "$rel:$n: символ CR (CRLF вместо LF)"
                if (line.contains(U_FFFD)) problems += "$rel:$n: символ U+FFFD — текст был перекодирован"
                if (QMARK_RUN.containsMatchIn(line)) {
                    problems += "$rel:$n: цепочка вопросительных знаков — кириллица потеряна при перекодировке"
                }
            }
        }

        if (problems.isNotEmpty()) {
            fail(
                "Исходники повреждены (${problems.size}):\n" + problems.joinToString("\n"),
            )
        }
    }

    /**
     * Съехавший на колонку 0 код внутри блока — тот же след порчи, только по отступам.
     * Простой «есть ли строка без отступа» не годится: колонка 0 законна для верхнего
     * уровня (package/import/class) и для закрывающей `}`, поэтому нужен мини-лексер,
     * который понимает, где мы реально находимся: внутри raw-строки текст в столбец 0
     * — это данные, а не код.
     */
    @Test
    fun `code lines inside blocks are indented`() {
        val problems = mutableListOf<String>()

        for (file in kotlinFiles()) {
            val rel = file.relativeTo(srcDir).path
            val text = String(file.readBytes(), Charsets.UTF_8)
            for (line in IndentScanner(text).offendingLines()) {
                problems += "$rel:$line: код внутри блока начинается с колонки 0"
            }
        }

        if (problems.isNotEmpty()) {
            fail("Съехавшие отступы (${problems.size}):\n" + problems.joinToString("\n"))
        }
    }

    @Test
    fun `hygiene test actually inspects the sources`() {
        // Защита от «тест зелёный, потому что файлы не нашлись».
        assertTrue(
            "найдено всего ${kotlinFiles().size} .kt — путь srcDir указывает не туда",
            kotlinFiles().size > 20,
        )
    }

    /**
     * Мини-лексер Kotlin, достаточный для одного вопроса: «эта строка — код верхнего
     * уровня или код внутри блока?». Отслеживает строки (с escape и шаблонами `$`+скобки),
     * символьные литералы, raw-строки, `//`- и `/*…*/`-комментарии; считает глубину
     * фигурных скобок вне строк и комментариев.
     *
     * Правило: на глубине больше нуля непустая строка кода не может начинаться с колонки 0.
     * Исключение — закрывающая `}` (её и Kotlin Formatter ставит в колонку 0) и любые
     * строки, попавшие внутрь raw-строки, блок-комментария или шаблона: там отступ
     * не имеет значения.
     */
    private class IndentScanner(private val text: String) {

        private enum class Kind { CODE, LINE, BLOCK, STRING, CHAR, RAW }

        private class Frame(val kind: Kind, var braces: Int = 0)

        private val quote = 0x22.toChar()
        private val apostrophe = 0x27.toChar()
        private val dollar = 0x24.toChar()
        private val openBrace = 0x7B.toChar()
        private val closeBrace = 0x7D.toChar()

        private val stack = ArrayDeque<Frame>().apply { addLast(Frame(Kind.CODE)) }
        private var depth = 0
        private val bad = mutableListOf<Int>()

        fun offendingLines(): List<Int> {
            text.split('\n').forEachIndexed { index, line -> scanLine(index + 1, line) }
            return bad
        }

        private fun scanLine(no: Int, line: String) {
            // `//`-комментарий не переживает конец строки: иначе следующая строка
            // навсегда осталась бы «внутри комментария» и потеряла проверку.
            while (stack.last().kind == Kind.LINE) stack.removeLast()

            val insideCode = stack.last().kind == Kind.CODE
            if (insideCode && depth > 0 && line.isNotBlank() && !isIndented(line) &&
                !startsWithClosingBrace(line)
            ) {
                bad += no
            }

            var i = 0
            while (i < line.length) i = step(line, i)
        }

        private fun isIndented(line: String) = line[0] == ' ' || line[0] == '\t'

        /** Закрывающая скобка на колонке 0 — нормальный Kotlin-стиль, а не дефект. */
        private fun startsWithClosingBrace(line: String) = line.trimStart()[0] == closeBrace

        private fun step(line: String, i: Int): Int {
            val frame = stack.last()
            when (frame.kind) {
                Kind.LINE -> return line.length
                Kind.BLOCK -> {
                    if (line.startsWith("*/", i)) stack.removeLast()
                    return i + 1
                }
                Kind.RAW -> {
                    if (isTripleQuote(line, i)) stack.removeLast()
                    return i + 1
                }
                Kind.STRING -> {
                    val c = line[i]
                    if (c == '\\') return i + 2
                    if (c == quote) {
                        stack.removeLast()
                        return i + 1
                    }
                    if (c == dollar && line.getOrNull(i + 1) == openBrace) {
                        stack.addLast(Frame(Kind.CODE))
                        return i + 2
                    }
                    return i + 1
                }
                Kind.CHAR -> {
                    val c = line[i]
                    if (c == '\\') return i + 2
                    if (c == apostrophe) stack.removeLast()
                    return i + 1
                }
                Kind.CODE -> {
                    if (line.startsWith("//", i)) {
                        stack.addLast(Frame(Kind.LINE))
                        return i + 2
                    }
                    if (line.startsWith("/*", i)) {
                        stack.addLast(Frame(Kind.BLOCK))
                        return i + 2
                    }
                    if (isTripleQuote(line, i)) {
                        stack.addLast(Frame(Kind.RAW))
                        return i + 3
                    }
                    val c = line[i]
                    if (c == quote) {
                        stack.addLast(Frame(Kind.STRING))
                        return i + 1
                    }
                    if (c == apostrophe) {
                        stack.addLast(Frame(Kind.CHAR))
                        return i + 1
                    }
                    if (c == openBrace) {
                        frame.braces++
                        depth++
                        return i + 1
                    }
                    if (c == closeBrace) {
                        if (frame.braces > 0) {
                            frame.braces--
                            depth--
                        } else if (stack.size > 1) {
                            // `}` без открытой в этом фрейме скобки = конец шаблона.
                            stack.removeLast()
                        }
                        return i + 1
                    }
                    return i + 1
                }
            }
        }

        private fun isTripleQuote(line: String, i: Int) =
            i + 2 < line.length && line[i] == quote && line[i + 1] == quote && line[i + 2] == quote
    }
}
