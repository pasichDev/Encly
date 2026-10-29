import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// The one door to cryptographic primitives (Crypto: AES-256-GCM, HKDF, PBKDF2, SHA-256, the CSPRNG) and
// BIP-39 mnemonics. The API is common code; the implementation is JCA, shared by the Android and the
// desktop target (jvmAndAndroidMain).
//
// There are no iOS targets: Apple's public CommonCrypto has no AES-GCM (CCCryptorGCM is private API) and
// CryptoKit cannot be called from Kotlin/Native without a Swift bridge, so a correct AES-GCM for iOS needs
// a decision about that bridge first. A placeholder with weaker crypto is not an option, so the targets
// are added together with the real implementation.
plugins {
    id("encly.kmp-library")
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
