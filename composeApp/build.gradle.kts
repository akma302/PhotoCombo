plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.okio)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.androidx.navigation3.ui)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Main-диспетчер корутин на desktop (нужен viewModelScope).
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
                // Compose UI Test для desktop: экранные тесты запускают окно без эмулятора.
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

// Компилятор Room запускается через KSP отдельно для каждой цели.
dependencies {
    add("kspDesktop", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Тесты на реальных фотографиях включаются только при явной передаче каталога:
// -Prealphotos="C:\Photos" — каталог прокидывается в тесты системным свойством.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("realphotos").orNull?.let { systemProperty("photocombo.realphotos", it) }
}

// Приложение desktop: даёт задачу :composeApp:run для запуска окна из исходников.
compose.desktop {
    application {
        mainClass = "com.combo.MainKt"
    }
}

// Галерея не поднимает DI, базу данных и пользовательскую библиотеку.
tasks.register<JavaExec>("runDesignSystemGallery") {
    group = "application"
    description = "Open the live PhotoCombo component gallery"
    mainClass.set("com.combo.ui.gallery.DesignSystemGalleryKt")
    classpath(kotlin.targets["desktop"].compilations["main"].output.allOutputs)
    classpath(configurations["desktopRuntimeClasspath"])
}
