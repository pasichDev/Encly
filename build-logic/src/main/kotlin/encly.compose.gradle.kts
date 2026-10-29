// Convention plugin `encly.compose`: Compose Multiplatform for a KMP module (the Compose compiler
// plugin and the JetBrains artifacts). Apply after `encly.kmp-library`.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        // Public composables take Modifier, Color, TextStyle and the like, so these are `api`.
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
        }
        // Compose on the desktop JVM draws with Skiko; currentOs picks its native part for this machine,
        // which the desktop tests need.
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
            // runComposeUiTest: Compose UI tests with a mouse and a keyboard on the desktop JVM.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}
