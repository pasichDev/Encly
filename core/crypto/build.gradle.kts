import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// The one door to cryptographic primitives (Crypto: AES-256-GCM, HKDF, PBKDF2, SHA-256, the CSPRNG) and
// BIP-39 mnemonics. The API is common code; the implementation is JCA on the JVM and Android
// (jvmAndAndroidMain) and Apple's own libraries on iOS (iosMain): CommonCrypto for SHA-256, HMAC and PBKDF2,
// SecRandomCopyBytes for randomness, and CryptoKit for AES-GCM, which CommonCrypto has no public API for.
// CryptoKit is Swift-only, so src/iosMain/swift/EnclyAesGcm.swift exports two C functions over it; the
// SwiftStaticLibrary tasks below compile that file for each SDK and cinterop links it into the klib
// (src/nativeInterop/cinterop/enclyaesgcm.def). Nothing is implemented by hand.
plugins {
    id("encly.kmp-library")
    id("encly.kmp-ios")
    id("encly.quality")
}

// Minimum line and branch coverage (%) of the whole module, enforced by koverVerify.
val cryptoMinCoverage = 100

kotlin {
    android {
        namespace = "com.pasich.encly.core.crypto"
    }

    sourceSets {
        commonMain.dependencies {
            // SensitiveDataCleaner wipes the intermediate key material.
            api(project(":core:common"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// The Swift bridge exists only where Xcode does; on other hosts Kotlin does not build the Apple targets.
if (OperatingSystem.current().isMacOsX) {
    val swiftTargets = mapOf(
        "iosArm64" to ("iphoneos" to "arm64-apple-ios15.0"),
        "iosSimulatorArm64" to ("iphonesimulator" to "arm64-apple-ios15.0-simulator"),
    )
    val cinteropDir = layout.projectDirectory.dir("src/nativeInterop/cinterop")
    kotlin.targets.withType<KotlinNativeTarget>().configureEach {
        val (sdkName, tripleName) = swiftTargets[name] ?: return@configureEach
        val swiftLibrary = tasks.register<SwiftStaticLibrary>("compileSwift${name.replaceFirstChar(Char::uppercase)}") {
            source.set(layout.projectDirectory.file("src/iosMain/swift/EnclyAesGcm.swift"))
            sdk.set(sdkName)
            triple.set(tripleName)
            libraryName.set("enclyaesgcm")
            outputDir.set(layout.buildDirectory.dir("swift/$name"))
        }
        compilations.getByName("main").cinterops.create("enclyaesgcm") {
            definitionFile.set(cinteropDir.file("enclyaesgcm.def"))
            includeDirs(cinteropDir)
            extraOpts("-libraryPath", swiftLibrary.flatMap { it.outputDir }.get().asFile.path)
            // The library is copied into the klib, so a change to it must run cinterop again.
            tasks.named(interopProcessingTaskName) {
                dependsOn(swiftLibrary)
                inputs.dir(swiftLibrary.flatMap { it.outputDir }).withPropertyName("swiftLibrary")
            }
        }
    }
}

kover {
    reports {
        total {
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Crypto line coverage") {
                    bound {
                        minValue = cryptoMinCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
                rule("Crypto branch coverage") {
                    bound {
                        minValue = cryptoMinCoverage
                        coverageUnits = CoverageUnit.BRANCH
                    }
                }
            }
        }
    }
}
