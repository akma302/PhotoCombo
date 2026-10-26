plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":data"))
            api(compose.ui)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite)
            api(libs.kotlinx.coroutines.core)
            api(libs.okio)
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":test-support"))
            }
        }
    }
}
