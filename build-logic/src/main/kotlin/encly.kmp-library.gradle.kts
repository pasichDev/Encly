// Convention plugin `encly.kmp-library`: a Kotlin Multiplatform library module. Targets are Android
// (the AGP KMP library plugin), a desktop JVM and the two iOS ones. Explicit API mode is on, so every
// public declaration is written out on purpose. The module sets its own `namespace`.

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    explicitApi()
    jvmToolchain(21)
    compilerOptions {
        // `expect class`/`expect object` declarations are a stable way to hide a platform type.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        compileSdk = 37
        minSdk = 26
        // Unit tests of the Android target run on the JVM (task `testAndroidHostTest`), with Robolectric
        // and Android resources where a module needs them.
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    // The public binary API is tracked in <module>/api/: `updateKotlinAbi` rewrites the dump and
    // `checkKotlinAbi` (part of `check`) fails when the code and the dump differ.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // Declaring the block enables the validation; the defaults are what we want.
    }
}
