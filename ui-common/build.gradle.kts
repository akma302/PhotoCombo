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
            api(project(":data"))
            implementation(project(":domain"))
            api(project(":design-system"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(libs.compose.material.ripple)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":test-support"))
            }
        }
    }
}
