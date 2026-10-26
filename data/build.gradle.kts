plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(compose.ui)
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":test-support"))
            }
        }
    }
}

dependencies { add("kspDesktop", libs.androidx.room.compiler) }
room { schemaDirectory("$projectDir/schemas") }
