package com.combo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Проверка архитектуры по исходникам: слои `core ← data ← domain ← feature ← ui` и
 * отсутствие циклов между пакетами.
 *
 * Правила слоёв (каждое закрывает зависимость, которая ломает независимость пакетов):
 *
 * - `core` не импортирует `data`, `domain`, `feature`, `ui`: ядро отрисовки не должно
 *   знать ни про базу, ни про экраны;
 * - `core.model` не импортирует ничего из `com.combo.*`: общие enum'ы — лист графа,
 *   иначе любой пакет, использующий enum, потянет за собой остальные;
 * - `data` не импортирует `domain`, `feature`, `ui`; `domain` — `feature`, `ui`;
 * - `feature.X` не импортирует `feature.Y`: фичи независимы друг от друга;
 * - подпакеты редактора `feature.builder.draft` и `feature.builder.history` не
 *   импортируют сам `feature.builder`: логика правок не зависит от экрана;
 * - `ui.common` не импортирует `feature`: общие компоненты не знают о конкретных экранах;
 * - `commonMain` не импортирует платформенные API (`android.*`, `java.*`, `javax.*`,
 *   `org.jetbrains.skia.*`): общий код обязан собираться под любую цель, а не только
 *   под desktop (это же делает будущий перенос на другие платформы механическим).
 *
 * Отдельный тест ищет циклы между любыми пакетами `com.combo.*`, чтобы не зависеть от
 * того, предусмотрел ли список правил конкретную пару пакетов.
 *
 * Верхний уровень (`com.combo.Const`, `App`, `MainActivity`) из проверок исключён: он
 * по определению зависит от всего. Проверяются `commonMain` и `desktopMain`, а не тесты: тесты
 * по определению импортируют что угодно.
 *
 * Пакет берётся из строки `package` в файле, а не из пути (путь содержит `src/commonMain/kotlin`
 * и ни с одним правилом не совпал бы). От проверки «вхолостую» защищают тесты
 * `every rule actually fires` и `architecture test actually inspects the sources`.
 */
class ArchitectureRulesTest {

    /** Корни исходников приложения: общий код и desktop. Тестовые наборы не проверяются. */
    private val srcRoots: List<File> = listOf("src/commonMain/kotlin", "src/desktopMain/kotlin")
        .map { File(System.getProperty("user.dir"), it) }
        .filter { it.isDirectory }

    /** Один проверенный файл: его пакет и списки импортов (все и только `com.combo.*`). */
    private data class Source(
        val label: String,
        val pkg: String,
        val imports: List<String>,
        val sourceSet: String = "",
        val allImports: List<String> = emptyList(),
    )

    private fun sources(): List<Source> =
        srcRoots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { root to it } }
            .sortedBy { (_, file) -> file.path }
            .map { (root, file) ->
                val lines = file.readLines()
                Source(
                    label = file.relativeTo(root).path.replace('\\', '/'),
                    pkg = lines.firstOrNull { it.startsWith("package ") }
                        ?.removePrefix("package ")?.trim()
                        // Файла без `package` в этом проекте быть не должно; если вдруг —
                        // берём путь, чтобы правило применилось по расположению, а не молча выпало.
                        ?: file.relativeTo(root).path.replace('\\', '/')
                            .substringBeforeLast('/', "").replace('/', '.'),
                    imports = lines.mapNotNull { line ->
                        Regex("^import\\s+(com\\.combo\\.[\\w.]+)").find(line)?.groupValues?.get(1)
                    },
                    // "src/commonMain/kotlin" → "commonMain": имя набора источников нужно
                    // правилу про платформенные импорты.
                    sourceSet = root.parentFile.name,
                    allImports = lines.mapNotNull { line ->
                        Regex("^import\\s+([\\w.]+)").find(line)?.groupValues?.get(1)
                    },
                )
            }
            .toList()

    /** Фичи приложения; используются для правила «feature.X не импортирует feature.Y». */
    private val featureRoots = listOf(
        "com.combo.feature.builder",
        "com.combo.feature.combo_list",
        "com.combo.feature.library",
        "com.combo.feature.viewer",
    )

    private fun violationsIn(sources: List<Source>): List<String> {
        val problems = mutableListOf<String>()

        for (s in sources) {
            fun forbid(prefixes: List<String>, rule: String) {
                for (imp in s.imports) {
                    if (prefixes.any { imp == it || imp.startsWith("$it.") }) {
                        problems += "${s.label}: [$rule] import $imp"
                    }
                }
            }

            // Подпакет фичи (`feature.builder.draft`) принадлежит фиче `builder`, поэтому
            // от соседних фич он закрыт так же, как и сама фича.
            val featureRoot = featureRoots.firstOrNull { s.pkg == it || s.pkg.startsWith("$it.") }
            val otherFeatures = if (featureRoot != null) {
                featureRoots.filterNot { it == featureRoot }
            } else {
                emptyList()
            }

            // Правила проверяются ПОДРЯД, а не через `when`: правило для всего `core`
            // покрывает и `core.render`, и `core.model`, и `when` остановился бы на первом
            // совпадении, так что более узкое правило (model — лист графа)
            // не выполнялось бы никогда.
            if (s.pkg.startsWith("com.combo.core")) {
                forbid(
                    listOf("com.combo.data", "com.combo.domain", "com.combo.feature", "com.combo.ui"),
                    "core не импортирует data/domain/feature/ui",
                )
            }
            if (s.pkg == "com.combo.core.model") {
                forbid(listOf("com.combo"), "core.model не импортирует ничего из com.combo.*")
            }
            if (s.pkg.startsWith("com.combo.data")) {
                forbid(
                    listOf("com.combo.domain", "com.combo.feature", "com.combo.ui"),
                    "data не импортирует domain/feature/ui",
                )
            }
            if (s.pkg.startsWith("com.combo.domain")) {
                forbid(
                    listOf("com.combo.feature", "com.combo.ui"),
                    "domain не импортирует feature/ui",
                )
            }
            if (s.pkg == "com.combo.ui.common" || s.pkg.startsWith("com.combo.ui.common.")) {
                forbid(listOf("com.combo.feature"), "ui.common не импортирует feature")
            }
            val ds = "com.combo.ui.common.designsystem"
            if (s.pkg == ds || s.pkg.startsWith("$ds.")) {
                val part = s.pkg.removePrefix("$ds.").substringBefore('.')
                val allowedParts = when (part) {
                    "tokens", "icons" -> setOf(part)
                    "theme" -> setOf("tokens", "theme")
                    "primitives" -> setOf("tokens", "theme", "primitives", "icons")
                    "components" -> setOf("tokens", "theme", "primitives", "icons", "components")
                    else -> emptySet()
                }
                for (imp in s.allImports.ifEmpty { s.imports }) {
                    val ownImport = imp.startsWith("$ds.")
                    val illegalInternal = ownImport && imp.removePrefix("$ds.").substringBefore('.') !in allowedParts
                    val illegalApplication = imp.startsWith("com.combo.") && !ownImport
                    val illegalExternal = !imp.startsWith("com.combo.") &&
                        !imp.startsWith("kotlin.") && !imp.startsWith("kotlinx.coroutines.") &&
                        !imp.startsWith("androidx.compose.")
                    val material = imp.startsWith("androidx.compose.material")
                    if (illegalInternal || illegalApplication || illegalExternal || material) {
                        problems += "${s.label}: [независимая DS и направление tokens ← theme ← primitives ← components] import $imp"
                    }
                }
            }
            if (featureRoot != null) {
                forbid(otherFeatures, "feature.X не импортирует feature.Y")
            }
            if (s.pkg == "com.combo.feature.builder.draft" || s.pkg == "com.combo.feature.builder.history") {
                for (imp in s.imports) {
                    if (builderRootImport.containsMatchIn(imp)) {
                        problems += "${s.label}: [draft/history не импортируют feature.builder] import $imp"
                    }
                }
            }
            if (s.sourceSet == "commonMain") {
                val platformPrefixes = listOf("android.", "java.", "javax.", "org.jetbrains.skia.")
                for (imp in s.allImports) {
                    if (platformPrefixes.any { imp == it.trimEnd('.') || imp.startsWith(it) }) {
                        problems += "${s.label}: [commonMain без платформенных импортов] import $imp"
                    }
                }
            }
        }
        return problems.distinct()
    }

    /**
     * Импорт символа из самого пакета `feature.builder` (но не из его подпакетов `draft` и
     * `history`): `com.combo.feature.builder.FitPreset` совпадает,
     * `com.combo.feature.builder.draft.ComboSnap` — нет.
     */
    private val builderRootImport = Regex("^com\\.combo\\.feature\\.builder\\.(?!(draft|history)(\\.|$))")

    // ---------- граф пакетов ----------

    /**
     * Самый длинный известный пакет, являющийся префиксом импорта. Импорт функции верхнего
     * уровня (`com.combo.core.render.buildItemFrame`) и вложенного класса
     * (`...ComboListViewModel.State`) в этом смысле ничем не отличается от импорта класса:
     * в обоих случаях ребро идёт в пакет, где лежит файл с символом.
     */
    private fun resolvePackage(importName: String, known: Set<String>): String? {
        var candidate = importName.trimEnd('.').substringBeforeLast('.', "")
        while (candidate.isNotEmpty()) {
            if (candidate in known) return candidate
            candidate = candidate.substringBeforeLast('.', "")
        }
        return null
    }

    /** Рёбра «пакет → пакеты, которые он импортирует». Корневой `com.combo` не участвует. */
    private fun packageGraph(sources: List<Source>): Map<String, Set<String>> {
        val known = sources.map { it.pkg }.toSet()
        val graph = mutableMapOf<String, MutableSet<String>>()
        for (s in sources) {
            if (s.pkg == "com.combo") continue
            val edges = graph.getOrPut(s.pkg) { mutableSetOf() }
            for (imp in s.imports) {
                val target = resolvePackage(imp, known) ?: continue
                if (target != s.pkg && target != "com.combo") edges += target
            }
        }
        return graph
    }

    /** Группы пакетов, которые достижимы друг из друга (компоненты сильной связности размера > 1). */
    private fun cycles(graph: Map<String, Set<String>>): List<List<String>> {
        val index = mutableMapOf<String, Int>()
        val low = mutableMapOf<String, Int>()
        val stack = ArrayDeque<String>()
        val onStack = mutableSetOf<String>()
        val result = mutableListOf<List<String>>()
        var counter = 0

        fun visit(v: String) {
            index[v] = counter
            low[v] = counter
            counter++
            stack.addLast(v)
            onStack += v
            for (w in graph[v].orEmpty()) {
                if (w !in index) {
                    visit(w)
                    low[v] = minOf(low.getValue(v), low.getValue(w))
                } else if (w in onStack) {
                    low[v] = minOf(low.getValue(v), index.getValue(w))
                }
            }
            if (low[v] == index[v]) {
                val component = mutableListOf<String>()
                while (true) {
                    val w = stack.removeLast()
                    onStack -= w
                    component += w
                    if (w == v) break
                }
                if (component.size > 1) result += component.sorted()
            }
        }

        for (v in graph.keys.sorted()) if (v !in index) visit(v)
        return result
    }

    @Test
    fun `layer rules hold`() {
        val problems = violationsIn(sources())
        if (problems.isNotEmpty()) {
            fail("Нарушения слоёв (${problems.size}):\n" + problems.joinToString("\n"))
        }
    }

    @Test
    fun `there are no cycles between packages`() {
        val found = cycles(packageGraph(sources()))
        if (found.isNotEmpty()) {
            fail(
                "Циклы между пакетами (${found.size}):\n" +
                    found.joinToString("\n") { it.joinToString(" ↔ ") },
            )
        }
    }

    /**
     * Защита от «тест зелёный, потому что ни одно правило никогда не срабатывает»:
     * каждому правилу подставляется синтетический файл, который его нарушает, а
     * законные переходы проверяются на отсутствие ложных срабатываний.
     */
    @Test
    fun `every rule actually fires`() {
        val cases = listOf(
            Source("a", "com.combo.core.render", listOf("com.combo.data.PhotoEntity")),
            Source("d", "com.combo.core.model", listOf("com.combo.core.render.Edge")),
            Source("e", "com.combo.data", listOf("com.combo.domain.PhotoRepository")),
            Source("f", "com.combo.domain", listOf("com.combo.feature.builder.Bar")),
            Source("g", "com.combo.feature.library", listOf("com.combo.feature.builder.Bar")),
            Source("h", "com.combo.feature.builder.draft", listOf("com.combo.feature.library.Bar")),
            Source("i", "com.combo.ui.common", listOf("com.combo.feature.builder.ThumbImage")),
            Source("j", "com.combo.feature.builder.draft", listOf("com.combo.feature.builder.FitPreset")),
            Source("k", "com.combo.feature.builder.history", listOf("com.combo.feature.builder.ComboBuilderViewModel")),
            Source(
                "m", "com.combo.ui.common", emptyList(),
                sourceSet = "commonMain", allImports = listOf("android.graphics.Bitmap"),
            ),
            Source(
                "n", "com.combo.core.util", emptyList(),
                sourceSet = "commonMain", allImports = listOf("java.io.File"),
            ),
            Source(
                "o", "com.combo.domain", emptyList(),
                sourceSet = "commonMain", allImports = listOf("javax.swing.JFrame"),
            ),
            Source(
                "p", "com.combo.core.image", emptyList(),
                sourceSet = "commonMain", allImports = listOf("org.jetbrains.skia.Image"),
            ),
        )
        val fired = violationsIn(cases)
        assertTrue(
            "ожидалось ${cases.size} нарушений, а сработало ${fired.size}:\n${fired.joinToString("\n")}",
            fired.size == cases.size,
        )

        // И обратная сторона: законные переходы нарушениями быть не должны.
        val legal = listOf(
            Source("l1", "com.combo.core.model", emptyList()),
            Source("l2", "com.combo.core.render", listOf("com.combo.core.model.FramingMode")),
            Source("l4", "com.combo.data", listOf("com.combo.core.render.Border")),
            Source("l5", "com.combo.domain", listOf("com.combo.data.PhotoEntity")),
            Source("l6", "com.combo.feature.builder.history", listOf("com.combo.feature.builder.draft.ComboSnap")),
            Source("l7", "com.combo.ui.common", listOf("com.combo.data.PhotoEntity")),
            Source("l8", "com.combo", listOf("com.combo.core.render.Border")),
            Source("l9", "com.combo.ui", listOf("com.combo.feature.builder.Bar")),
            Source("l10", "com.combo.feature.builder", listOf("com.combo.feature.builder.draft.DraftEditor")),
            Source(
                "l11", "com.combo.ui.common", emptyList(),
                sourceSet = "commonMain", allImports = listOf("androidx.compose.ui.Modifier", "kotlin.math.abs"),
            ),
            Source(
                "l12", "com.combo.ui.common", emptyList(),
                sourceSet = "desktopMain", allImports = listOf("java.io.File", "org.jetbrains.skia.Image"),
            ),
        )
        val falsePositives = violationsIn(legal)
        if (falsePositives.isNotEmpty()) {
            fail("Ложные срабатывания (${falsePositives.size}):\n" + falsePositives.joinToString("\n"))
        }
    }

    /** Поиск циклов обязан находить цикл и не придумывать его там, где его нет. */
    @Test
    fun `cycle detection finds cycles and only cycles`() {
        val cyclic = mapOf(
            "a" to setOf("b"),
            "b" to setOf("c"),
            "c" to setOf("a"),
            "d" to setOf("a"),
        )
        assertEquals(listOf(listOf("a", "b", "c")), cycles(cyclic))

        val acyclic = mapOf(
            "a" to setOf("b", "c"),
            "b" to setOf("c"),
            "c" to emptySet(),
        )
        assertTrue(cycles(acyclic).isEmpty())
    }

    @Test
    fun `design system dependencies are constrained at every level`() {
        val ds = "com.combo.ui.common.designsystem"
        val forbidden = listOf(
            "tokens" to "$ds.theme.PcTheme",
            "theme" to "$ds.components.PcButton",
            "primitives" to "$ds.components.PcButton",
            "icons" to "$ds.tokens.PcColors",
            "components" to "com.combo.core.model.FramingMode",
            "components" to "com.combo.domain.PhotoRepository",
            "components" to "com.combo.ui.common.BackDispatcher",
            "components" to "org.koin.compose.koinInject",
            "components" to "androidx.lifecycle.ViewModel",
            "components" to "androidx.compose.material3.Button",
            "icons" to "androidx.compose.material.icons.Icons",
        )
        forbidden.forEach { (part, imp) ->
            assertTrue("$part must reject $imp", violationsIn(listOf(
                Source(part, "$ds.$part", listOf(imp), allImports = listOf(imp)),
            )).isNotEmpty())
        }
        val legal = listOf(
            "theme" to "$ds.tokens.PcColors",
            "primitives" to "$ds.theme.PcTheme",
            "components" to "$ds.primitives.PcText",
            "components" to "$ds.icons.PcIcons",
            "icons" to "androidx.compose.ui.graphics.vector.ImageVector",
        )
        legal.forEach { (part, imp) ->
            assertEquals(emptyList<String>(), violationsIn(listOf(
                Source(part, "$ds.$part", listOf(imp), allImports = listOf(imp)),
            )))
        }
        assertTrue(violationsIn(listOf(Source("nested", "$ds.components", listOf("com.combo.feature.viewer.Viewer")))).isNotEmpty())
    }

    /** Ребро идёт в самый длинный известный пакет, а не в «псевдопакет» из имени функции. */
    @Test
    fun `an import resolves to the longest known package`() {
        val known = setOf("com.combo.core.render", "com.combo.feature.combo_list", "com.combo.feature")
        assertEquals("com.combo.core.render", resolvePackage("com.combo.core.render.buildItemFrame", known))
        assertEquals(
            "com.combo.feature.combo_list",
            resolvePackage("com.combo.feature.combo_list.ComboListViewModel.State", known),
        )
        assertEquals("com.combo.feature", resolvePackage("com.combo.feature.Foo", known))
        assertNull(resolvePackage("com.combo.data.PhotoEntity", known))
    }

    /** Защита от «тест зелёный, потому что файлы не нашлись». */
    @Test
    fun `architecture test actually inspects the sources`() {
        val all = sources()
        assertTrue(
            "найдено всего ${all.size} .kt в commonMain/desktopMain — пути srcRoots указывают не туда",
            all.size > 20,
        )
        assertTrue(
            "ни один файл не снял package — проверка слоёв не сработает",
            all.all { it.pkg.startsWith("com.combo") },
        )
    }
}
