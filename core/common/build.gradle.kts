import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// The smallest shared layer: text that is stored as a resource id (UiText, AppStrings), the loading state
// of a screen, the flags store and timer interfaces that platform code implements, the sensitive-data
// wipe and the failure-only logger. It depends on nothing of Encly; the Android implementations of its
// interfaces live in :app.
plugins {
    id("encly.kmp-library")
    id("encly.kmp-ios")
    id("encly.quality")
}

// Minimum line coverage (%) of the whole module, enforced by koverVerify.
val commonMinCoverage = 85

kotlin {
    android {
        namespace = "com.pasich.encly.core.common"
    }

    sourceSets {
        commonMain.dependencies {
            // LoadState is built on Flow; the other interfaces name no library.
            api(libs.kotlinx.coroutines.core)
            // UiText and AppStrings name StringResource; the strings themselves are in :core:resources.
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

kover {
    reports {
        total {
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Common line coverage") {
                    bound {
                        minValue = commonMinCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
            }
        }
    }
}
