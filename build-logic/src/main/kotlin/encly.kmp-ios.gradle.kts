// Convention plugin `encly.kmp-ios`: adds the two iOS targets to a module that uses `encly.kmp-library`.
// A module applies it only when everything it depends on can run on iOS.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
}
