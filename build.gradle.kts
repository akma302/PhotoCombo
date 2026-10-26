plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// Производственный граф фиксирует границы слоёв; тестовые зависимости проверяются отдельно компилятором.
val moduleDependencies = mapOf(
    ":core" to emptySet<String>(),
    ":data" to setOf(":core"),
    ":domain" to setOf(":core", ":data"),
    ":design-system" to emptySet(),
    ":ui-common" to setOf(":core", ":data", ":domain", ":design-system"),
    ":feature:builder" to setOf(":core", ":data", ":domain", ":design-system", ":ui-common"),
    ":feature:library" to setOf(":core", ":data", ":domain", ":design-system", ":ui-common"),
    ":feature:combo-list" to setOf(":core", ":data", ":domain", ":design-system", ":ui-common"),
    ":feature:viewer" to setOf(":core", ":data", ":domain", ":design-system", ":ui-common"),
    ":test-support" to setOf(":core", ":data", ":domain", ":design-system", ":ui-common"),
    ":composeApp" to setOf(":core", ":data", ":domain", ":design-system", ":ui-common",
        ":feature:builder", ":feature:library", ":feature:combo-list", ":feature:viewer"),
)

val verifyModuleGraph by tasks.registering {
    group = "verification"
    description = "Verify the production module graph and keep test-support out of production dependencies"
    doLast {
        val configured = allprojects.filter { it.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") }
            .map { it.path }.toSet()
        check(configured == moduleDependencies.keys) { "Unregistered modules: ${configured - moduleDependencies.keys}" }
        moduleDependencies.forEach { (path, expected) ->
            val actual = project(path).configurations
                .filterNot { it.name.contains("test", ignoreCase = true) }
                .flatMap { it.dependencies.withType<ProjectDependency>().map { dependency -> dependency.path } }
                .toSet()
            check(actual == expected) { "$path: expected $expected, actual $actual" }
        }
    }
}

subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("photocombo.root", rootProject.projectDir.absolutePath)
        systemProperty("photocombo.modules", moduleDependencies.keys.joinToString("|") { it.removePrefix(":").replace(':', '/') })
        providers.gradleProperty("realphotos").orNull?.let { systemProperty("photocombo.realphotos", it) }
        // UI-тесты каждого модуля выполняются в одном тестовом процессе.
        maxParallelForks = 1
    }
}

tasks.register("desktopTest") {
    group = "verification"
    description = "Run every module's desktop tests, including application integration tests"
    dependsOn(verifyModuleGraph)
    dependsOn(moduleDependencies.keys.map { "$it:desktopTest" })
}
tasks.register("check") {
    group = "verification"
    dependsOn("desktopTestReport")
}

tasks.register<TestReport>("desktopTestReport") {
    group = "verification"
    description = "Run all desktop tests and collect their reports"
    dependsOn("desktopTest")
    destinationDirectory.set(layout.buildDirectory.dir("reports/tests/desktop"))
    testResults.from(moduleDependencies.keys.filterNot { it == ":test-support" }.map {
        project(it).layout.buildDirectory.dir("test-results/desktopTest/binary")
    })
}
