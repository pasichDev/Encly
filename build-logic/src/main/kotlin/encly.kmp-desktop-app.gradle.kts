// Convention plugin `encly.kmp-desktop-app`: a Kotlin Multiplatform module with the desktop JVM target only
// (`desktopMain`, `desktopTest`; no Android, no iOS) and Compose Multiplatform, for the desktop application.
// It declares the target first because `encly.compose` configures the `desktopTest` source set when it is applied.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")
}

apply(plugin = "encly.compose")
