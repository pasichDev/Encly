import dev.zacsweers.metro.gradle.OptionalBindingBehavior
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// The platform-free security core: the key hierarchy (vault store, PIN, wipe-PIN, duress and recovery
// slots), the unlock and lockout logic, the session lock, and the backup file format and cipher. It
// is written against interfaces for everything a platform provides (the flag store, the hardware PIN
// factor, the biometric prompt, the lockout clock, the device lock watcher, the vault file's fsync, the
// encrypted database); :app implements them with Android types and binds them in its graph.
//
// There are no iOS targets because :core:crypto has none yet (see its build file).
plugins {
    id("encly.kmp-library")
    id("encly.quality")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

// Findings that predate the move out of the app are captured in detekt-baseline.xml, which should only
// ever shrink.
detekt {
    baseline = file("detekt-baseline.xml")
}

// Minimum line coverage (%) of the whole module, enforced by koverVerify.
val securityMinCoverage = 85

// The same as in :app. A Kotlin default on an injected constructor parameter stays a plain default: the
// parameter is always taken from the graph, and a missing binding is an error instead of silently falling
// back to the default (e.g. a lock event source that never locks).
metro {
    optionalBindingBehavior.set(OptionalBindingBehavior.DISABLED)
}

kotlin {
    android {
        namespace = "com.pasich.encly.core.security"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:crypto"))
            // IOException is part of the contract of VaultFileIo.
            api(libs.kotlinx.io.core)
            // SessionLockManager is a lifecycle observer.
            api(libs.androidx.lifecycle.common)
            // The backup payload is @Serializable.
            api(libs.kotlinx.serialization.json)
            // SynchronizedObject and synchronized {}: the locks of the stores and managers.
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // JUnit and Mockito run on both JVM targets: the tests that need java.io, threads or mocks.
        getByName("jvmAndAndroidTest").dependencies {
            implementation(libs.junit)
            implementation(libs.mockito.core)
        }
    }
}

kover {
    reports {
        total {
            filters {
                // Code the Metro compiler plugin writes, not this project.
                excludes {
                    classes(
                        "*MetroFactory*",
                        "*MetroContributionTo*",
                        "*MetroMembersInjector*",
                    )
                }
            }
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Security line coverage") {
                    bound {
                        minValue = securityMinCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
            }
        }
    }
}
