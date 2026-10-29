// Every string and plural the UI shows, in all nine languages, as Compose Multiplatform resources. The
// translations are kept in Android's strings.xml format, which is what Weblate reads and writes
// (src/commonMain/strings/values[-<lang>]/*.xml); a build step turns them into the files Compose
// resources read. The generated `Res` class is public, so the app and the feature modules read
// `Res.string.*` / `Res.plurals.*`. Strings that Android itself needs (the launcher label) stay in the
// app's res/.
plugins {
    id("encly.kmp-library")
    id("encly.compose")
}

kotlin {
    android {
        namespace = "com.pasich.encly.core.resources"
        // The Android target packages the composeResources files as assets, which needs the AGP
        // resource pipeline (off by default in the KMP library plugin).
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // StringResource and PluralStringResource are part of the API of the generated Res class.
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            // Reading the resources from the Android assets runs on the JVM under Robolectric.
            implementation(libs.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
    }
}

// Android's escapes and unnumbered formats are not Compose resources' (see ConvertAndroidStrings in
// build-logic), so the Weblate-managed files are converted, and the result is the composeResources
// directory of commonMain.
val convertAndroidStrings by tasks.registering(ConvertAndroidStrings::class) {
    sources.set(layout.projectDirectory.dir("src/commonMain/strings"))
    output.set(layout.buildDirectory.dir("generated/androidStrings/composeResources"))
}

compose.resources {
    customDirectory(sourceSetName = "commonMain", directoryProvider = convertAndroidStrings.flatMap { it.output })
    publicResClass = true
    packageOfResClass = "com.pasich.encly.core.resources"
    generateResClass = always
}

// The public API is the generated Res class: one accessor per string, so an API dump would change with
// every string a translator or a developer adds. The module has no hand-written API to guard.
tasks.matching { it.name.contains("KotlinAbi") }.configureEach {
    enabled = false
}
