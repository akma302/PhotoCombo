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
            api(project(":domain"))
            implementation(project(":design-system"))
            implementation(project(":ui-common"))
            api(compose.runtime)
            implementation(compose.foundation)
            api(compose.ui)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.compose)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(compose.material3)
            implementation(libs.compose.material.ripple)
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":test-support"))
            }
        }
    }
}
