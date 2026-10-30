plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":data"))
            implementation(project(":domain"))
            implementation(project(":design-system"))
            implementation(project(":ui-common"))
            implementation(project(":feature:builder"))
            implementation(project(":feature:library"))
            implementation(project(":feature:combo-list"))
            implementation(project(":feature:viewer"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.room.runtime)
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
                implementation("org.jetbrains.runtime:jbr-api:1.9.0")
                // Main-диспетчер корутин на desktop (нужен viewModelScope).
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":test-support"))
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
                // Compose UI Test для desktop: экранные тесты запускают окно без эмулятора.
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
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
    mainClass.set("com.combo.ui.GalleryLauncherKt")
    classpath(kotlin.targets["desktop"].compilations["main"].output.allOutputs)
    classpath(configurations["desktopRuntimeClasspath"])
}


// Isolated window preview: no database or user library.
tasks.register<JavaExec>("runTitleBarPreview") {
    group = "application"
    mainClass.set("com.combo.ui.TitleBarPreviewKt")
    classpath(kotlin.targets["desktop"].compilations["main"].output.allOutputs)
    classpath(configurations["desktopRuntimeClasspath"])
    providers.gradleProperty("titlebarJava").orNull?.let { setExecutable(it) }
}
