plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            dependencies {
                api(project(":core"))
                api(project(":data"))
                api(project(":domain"))
                api(project(":design-system"))
                api(project(":ui-common"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.ui)
                api(compose.material3)
                api(compose.desktop.currentOs)
                api(compose.desktop.uiTestJUnit4)
                api(libs.junit)
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.coroutines.swing)
                api(libs.kotlinx.serialization.json)
                api(libs.okio)
                api(libs.androidx.room.runtime)
                api(libs.androidx.sqlite.bundled)
                api(libs.koin.core)
                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)
                api(libs.androidx.lifecycle.viewmodel.compose)
                api(libs.androidx.lifecycle.viewmodel.savedstate)
            }
        }
    }
}
