import dev.zacsweers.metro.gradle.OptionalBindingBehavior
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

// The desktop application (Windows, macOS, Linux): Compose Multiplatform on the JVM around :shared, with the
// desktop implementations of everything :app binds on Android (the Metro graph, the platform services, the OS
// secret store behind the PIN, the window, the menus). No network code: see README.md.
plugins {
    id("encly.kmp-desktop-app")
    id("encly.quality")
    alias(libs.plugins.metro)
}

// Same version file as the Android app, so one bump releases both.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = listOf("VERSION_MAJOR", "VERSION_MINOR", "VERSION_PATCH")
    .joinToString(".") { versionProps.getProperty(it).trim() }

// Minimum line coverage (%) of the module, enforced by koverVerify. The exclusions are listed below.
val desktopMinCoverage = 85

// The same as in :app. A Kotlin default on an injected constructor parameter stays a plain default: the
// parameter is always taken from the graph, and a missing binding is an error instead of silently falling
// back to the default (e.g. a lock event source that never locks).
metro {
    optionalBindingBehavior.set(OptionalBindingBehavior.DISABLED)
}

detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"), files("config/detekt.yml"))
}

// The version the app shows in About: a classpath resource written from version.properties.
val buildInfoDir = layout.buildDirectory.dir("generated/buildinfo")
val writeBuildInfo = tasks.register("writeDesktopBuildInfo") {
    val dir = buildInfoDir
    val version = appVersionName
    inputs.property("version", version)
    outputs.dir(dir)
    doLast {
        val target = dir.get().asFile
        target.mkdirs()
        target.resolve("encly-build.properties").writeText("version=$version\n")
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    sourceSets {
        getByName("desktopMain") {
            resources.srcDir(writeBuildInfo)
            dependencies {
                implementation(project(":shared"))
                implementation(project(":core:keystore"))
                implementation(compose.desktop.currentOs)
                // Dispatchers.Main is the AWT event thread: viewModelScope and the lifecycle collectors need it.
                implementation(libs.kotlinx.coroutines.swing)
                // The native calls: OS lock state, capture exclusion, uptime. Ships with :core:keystore already.
                implementation(libs.jna)
                implementation(libs.jna.platform)
                // The freedesktop portal and logind over the local D-Bus socket (AF_UNIX, no network).
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport)
                runtimeOnly(libs.slf4j.nop)
                // Note to PDF and to the printer, built in memory (Apache-2.0).
                implementation(libs.pdfbox)
            }
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.pasich.encly.desktop.MainKt"

        // No --add-opens is needed: the macOS window is reached through the Objective-C runtime, not through
        // AWT's private peer classes. Nothing here opens a network connection.
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dapple.awt.application.name=Encly")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Encly"
            packageVersion = appVersionName
            description = "Offline encrypted notes and tasks"
            vendor = "Encly"
            copyright = "Copyright 2026 Encly contributors. Apache-2.0."
            licenseFile.set(rootProject.file("LICENSE"))

            // The JDK modules the app needs beyond the defaults; `suggestRuntimeModules` lists what is missing.
            // java.desktop (AWT, Swing), java.prefs and java.naming come with the defaults of Compose.
            modules("java.instrument", "jdk.unsupported", "java.management", "java.sql", "java.logging", "jdk.security.auth")

            // Encly backup files open Encly (a double click starts the restore flow). The type is Encly's own,
            // never application/octet-stream, which would claim every binary file.
            fileAssociation(
                mimeType = "application/vnd.encly.backup",
                extension = "enclybak",
                description = "Encly backup",
                linuxIconFile = file("packaging/icons/icon.png"),
                windowsIconFile = file("packaging/icons/icon.ico"),
                macOSIconFile = file("packaging/icons/icon.icns"),
            )

            windows {
                iconFile.set(file("packaging/icons/icon.ico"))
                menuGroup = "Encly"
                menu = true
                shortcut = true
                perUserInstall = true
                // Fixed, so a new version upgrades the installed one instead of installing beside it.
                upgradeUuid = "6f1d0c52-3b0e-4b46-9a7e-2f5d7c9b4e18"
            }
            macOS {
                iconFile.set(file("packaging/icons/icon.icns"))
                bundleID = "com.pasich.encly"
                dockName = "Encly"
                // Hardened-runtime exceptions of the JVM, for the app and for its bundled runtime.
                entitlementsFile.set(file("packaging/macos/entitlements.plist"))
                runtimeEntitlementsFile.set(file("packaging/macos/entitlements.plist"))
                // Signing and notarization are off unless the environment sets these (the CI does it from
                // secrets; see README.md, "Signing the desktop installers"). The identity is the full name, e.g.
                // "Developer ID Application: Name (TEAMID)". Without ENCLY_MACOS_KEYCHAIN the login keychain is used.
                val identity = providers.environmentVariable("ENCLY_MACOS_SIGN_IDENTITY").orNull
                if (!identity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        this.identity.set(identity)
                        providers.environmentVariable("ENCLY_MACOS_KEYCHAIN").orNull
                            ?.takeIf { it.isNotBlank() }
                            ?.let { keychain.set(it) }
                    }
                    notarization {
                        appleID.set(providers.environmentVariable("ENCLY_MACOS_NOTARY_APPLE_ID"))
                        password.set(providers.environmentVariable("ENCLY_MACOS_NOTARY_PASSWORD"))
                        teamID.set(providers.environmentVariable("ENCLY_MACOS_NOTARY_TEAM_ID"))
                    }
                }
            }
            linux {
                iconFile.set(file("packaging/icons/icon.png"))
                packageName = "encly"
                debMaintainer = "pasichdev@users.noreply.github.com"
                menuGroup = "Office"
                appCategory = "Office"
            }
        }
    }
}

// Kover: line coverage of the module over the desktop tests. Excluded are only the entry point and the code
// that talks to the operating system and cannot run on a headless CI machine: the JNA and D-Bus glue
// (`nativeos`), the portal dialogs, and the `Window` host (needs a real AWT window).
kover {
    reports {
        total {
            filters {
                excludes {
                    classes(
                        "*ComposableSingletons*",
                        "*MetroFactory*",
                        "*MetroContributionTo*",
                        "*MetroMembersInjector*",
                        "com.pasich.encly.desktop.MainKt*",
                        "com.pasich.encly.desktop.nativeos.*",
                        "com.pasich.encly.desktop.window.WindowHost*",
                    )
                }
            }
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Desktop line coverage") {
                    bound {
                        minValue = desktopMinCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
            }
        }
    }
}
