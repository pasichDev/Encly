// Convention plugin `encly.kmp-library`: a Kotlin Multiplatform library module. Targets are Android
// (the AGP KMP library plugin) and a desktop JVM; the two iOS ones come from `encly.kmp-ios`. Explicit
// API mode is on, so every public declaration is written out on purpose. The module sets its own
// `namespace`.
//
// Source sets: besides the default hierarchy (commonMain, iosMain, ...) the group `jvmAndAndroid` holds
// what the Android and the desktop target share because both run on the JVM (`jvmAndAndroidMain`,
// `jvmAndAndroidTest`): the JCA crypto, java.io, JUnit and Mockito tests. A module with no code for
// the group simply leaves the directory out.

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

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

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                // withAndroidTarget() matches only the legacy androidTarget(); the AGP KMP library target
                // is a plain androidJvm target.
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    // The public binary API is tracked in <module>/api/: `updateKotlinAbi` rewrites the dump and
    // `checkKotlinAbi` (part of `check`) fails when the code and the dump differ.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // Declaring the block enables the validation; the defaults are what we want.
    }
}
