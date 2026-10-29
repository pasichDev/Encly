import dev.zacsweers.metro.gradle.OptionalBindingBehavior
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Everything of the app that is not Android-specific, as a Kotlin Multiplatform library (Android and desktop
// JVM): the domain, the repositories and the backup, import, export and hand-off logic, and the whole UI
// (design system, screens, dialogs, navigation, ViewModels) in Compose Multiplatform. What the UI needs from
// the platform is an interface in `com.pasich.encly.platform` that the host app implements (document
// pickers, links, toasts, the sensitive clipboard, the window, the biometric host, the in-app language).
//
// iOS (iosArm64, iosSimulatorArm64) is a target too: its host (the Metro graph, the platform services, the
// biometric slot, the privacy cover, MainViewController) is in `iosMain` and is built into the static framework
// `Shared` that the Xcode project in `iosApp/` embeds. The Android and desktop hosts are in :app and :desktopApp.
plugins {
    id("encly.kmp-library")
    id("encly.kmp-ios")
    id("encly.compose")
    id("encly.quality")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

// The shared detekt config plus the module's own (the tests are left out of more rules). Findings that predate
// the move out of the app are captured in detekt-baseline.xml, which should only ever shrink.
detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"), files("config/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

// Minimum line coverage (%) of the whole module, enforced by koverVerify. The same bar the app had.
val sharedMinCoverage = 85

// The same as in :app. A Kotlin default on an injected constructor parameter stays a plain default: the
// parameter is always taken from the graph, and a missing binding is an error instead of silently falling
// back to the default (e.g. a lock event source that never locks).
metro {
    optionalBindingBehavior.set(OptionalBindingBehavior.DISABLED)
}

kotlin {
    // An application layer, not a library: its classes are public because the host app and the tests reach
    // them, not because they are a published API.
    explicitApi = ExplicitApiMode.Disabled

    compilerOptions {
        // BackHandler of Compose Multiplatform is still marked experimental.
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
    }

    android {
        namespace = "com.pasich.encly.shared"
        // The Android target packages the composeResources files (fonts, the brand mark) as assets, which
        // needs the AGP resource pipeline (off by default in the KMP library plugin).
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:crypto"))
            api(project(":core:security"))
            api(project(":core:database"))
            api(project(":core:resources"))
            api(project(":editor"))

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.json.io)
            implementation(libs.kotlinx.atomicfu)

            // BackHandler (the system Back, on Android the predictive back gesture too).
            implementation(libs.compose.ui.backhandler)
            // Compose Multiplatform resources (the fonts and the brand mark of this module).
            implementation(libs.compose.components.resources)
            // Navigation, ViewModels and lifecycle for Compose Multiplatform.
            api(libs.compose.navigation)
            api(libs.compose.lifecycle.viewmodel)
            api(libs.compose.lifecycle.viewmodel.savedstate)
            api(libs.compose.lifecycle.runtime)

            // Metro's ViewModel integration (the graph itself is declared in the app).
            api(libs.metrox.viewmodel)
            api(libs.metrox.viewmodel.compose)

            // The settings store (DataStore<Preferences>); its Android factory is the app's.
            api(libs.androidx.datastore.preferences)
            // Room's transaction helpers used by the repositories.
            api(libs.androidx.room.runtime)

            implementation(libs.icons.lucide)
            implementation(libs.reorderable)
        }
        androidMain.dependencies {
            // The Android navigation host runs on the version the app tested (the Multiplatform artifact
            // resolves to an older androidx one).
            api(libs.androidx.navigation.compose)
            implementation(libs.androidx.core.ktx)
        }
        // The Keychain behind the PIN's device secret and the biometric slot (the iOS host's).
        getByName("iosMain").dependencies {
            implementation(project(":core:keystore"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // JUnit and Mockito run on both JVM targets: the tests that need java.io, threads or mocks.
        getByName("jvmAndAndroidTest").dependencies {
            implementation(libs.junit)
            implementation(libs.mockito.core)
            implementation(libs.kotlinx.coroutines.test)
        }
        // The real exports of other apps that the import tests read (classpath resources of both JVM targets).
        getByName("desktopTest").resources.srcDir("src/jvmAndAndroidTest/resources")
        getByName("androidHostTest").resources.srcDir("src/jvmAndAndroidTest/resources")
        getByName("androidHostTest").dependencies {
            // Compose UI tests run on the JVM under Robolectric, no emulator needed.
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.activity.compose)
            implementation(libs.mockito.core)
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

// The one framework of the iOS app: static, so the Xcode project links a single binary and needs no embedding
// step beyond the Compose resources. `./gradlew :shared:embedAndSignAppleFrameworkForXcode` builds it for Xcode.
kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    binaries.framework {
        baseName = "Shared"
        isStatic = true
    }
}

compose.resources {
    // Only this module reads them (the fonts and the brand mark); the strings are :core:resources'.
    packageOfResClass = "com.pasich.encly.shared.resources"
    publicResClass = false
    generateResClass = always
}

// An application layer has no published API to dump.
tasks.matching { it.name.contains("KotlinAbi") }.configureEach {
    enabled = false
}

// Coverage: `./gradlew :shared:koverVerify` over the desktop JVM tests and the Android host tests.
kover {
    reports {
        total {
            filters {
                // Code the compilers and plugins write, not this project.
                excludes {
                    classes(
                        "*ComposableSingletons*",
                        "*MetroFactory*",
                        "*MetroContributionTo*",
                        "*MetroMembersInjector*",
                        "com.pasich.encly.shared.resources.*",
                    )
                }
            }
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Shared line coverage") {
                    bound {
                        minValue = sharedMinCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
            }
        }
    }
}
