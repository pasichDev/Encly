// Convention plugin `encly.kmp-ios`: adds the two iOS targets to a module that uses `encly.kmp-library`.
// A module applies it only when everything it depends on can run on iOS; `:core:crypto` and the modules
// above it have no iOS implementation of AES-GCM yet and stay without it.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
}
