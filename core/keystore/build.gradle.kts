import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// The desktop implementation of the device-bound half of the PIN key (PinHardwareFactor): an HMAC key that
// lives only in the operating system's secret store of the signed-in user (Windows DPAPI, the macOS login
// Keychain, the freedesktop Secret Service on Linux). See DesktopPinFactor.
//
// Only the desktop JVM target has code. The Android target exists because the convention plugin adds it to
// every module (the app has its own Keystore implementation); it compiles nothing.
plugins {
    id("encly.kmp-library")
    id("encly.quality")
}

// Minimum line and branch coverage (%) of the unit-tested code, enforced by koverVerify. The three OS
// backends are excluded: they only run against a real secret store (the keystore-it CI job).
val keystoreMinLineCoverage = 90
val keystoreMinBranchCoverage = 80

kotlin {
    android {
        namespace = "com.pasich.encly.core.keystore"
    }

    sourceSets {
        getByName("desktopMain").dependencies {
            // PinHardwareFactor and PinFactorException are part of the public contract of DesktopPinFactor.
            api(project(":core:security"))
            implementation(project(":core:common"))
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.dbus.java.core)
            implementation(libs.dbus.java.transport)
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            // dbus-java logs through SLF4J; without a binding it prints a warning to stderr.
            runtimeOnly(libs.slf4j.nop)
        }
    }
}

kover {
    reports {
        total {
            filters {
                excludes {
                    classes(
                        "com.pasich.encly.core.keystore.WindowsDpapiSecretStore*",
                        "com.pasich.encly.core.keystore.MacKeychainSecretStore*",
                        "com.pasich.encly.core.keystore.LinuxSecretServiceStore*",
                        // The dbus-java interfaces and structs of the Secret Service.
                        "com.pasich.encly.core.keystore.Secret*Api*",
                        "com.pasich.encly.core.keystore.SecretStruct",
                        "com.pasich.encly.core.keystore.*Result",
                    )
                }
            }
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Keystore line coverage") {
                    bound {
                        minValue = keystoreMinLineCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
                rule("Keystore branch coverage") {
                    bound {
                        minValue = keystoreMinBranchCoverage
                        coverageUnits = CoverageUnit.BRANCH
                    }
                }
            }
        }
    }
}
