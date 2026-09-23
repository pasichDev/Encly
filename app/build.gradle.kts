import java.util.Properties

// Minimum line coverage (%) of com.pasich.encly.core.security enforced by koverVerify.
val koverMinSecurityLineCoverage = 40

// Derive the app version from the tracked root `version.properties` so versionName and
// versionCode always stay in sync and only one file needs bumping per release.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionMajor = versionProps.getProperty("VERSION_MAJOR").trim().toInt()
val appVersionMinor = versionProps.getProperty("VERSION_MINOR").trim().toInt()
val appVersionPatch = versionProps.getProperty("VERSION_PATCH").trim().toInt()
val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"
val appVersionCode = appVersionMajor * 10000 + appVersionMinor * 100 + appVersionPatch

// Release signing. Environment variables (CI secrets, see .github/workflows/release.yml) take
// precedence over an untracked keystore.properties. With neither, release stays unsigned.
val keystoreProps: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

fun signingValue(envName: String, propName: String): String? =
    providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProps?.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("ENCLY_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ENCLY_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ENCLY_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ENCLY_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Static analysis (default rules + Jetpack Compose rules, config/detekt/detekt.yml). Legacy
// findings are captured in detekt-baseline.xml so only NEW issues fail. Fix new findings instead
// of regenerating the baseline; it should only ever shrink.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$projectDir/detekt-baseline.xml")
}

android {
    compileSdk = 36
    namespace = "com.pasich.encly"

    defaultConfig {
        applicationId = "com.pasich.encly"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/INDEX.LIST",
            )
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    // Two distribution channels built from the same source and the same applicationId, so an
    // existing install keeps its data whichever channel upgrades it. `fdroid` carries no
    // store-specific UI (no "Rate app" link to Google Play).
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "STORE_RATING_ENABLED", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "STORE_RATING_ENABLED", "true")
        }
    }

    // Google Play serves the `play` bundle (bundlePlayRelease). Language splits stay off: Play
    // would otherwise install only the device language's resources, and the in-app language
    // picker (Settings -> Language) could not switch to any other of the bundled languages.
    bundle {
        language {
            enableSplit = false
        }
    }

    // Do not embed the Google-encrypted dependency metadata blob in the APK signing block or
    // the bundle: F-Droid rejects it and it defeats reproducible builds.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Keep the commit hash (META-INF/version-control-info.textproto) out of release APKs and
            // bundles: the output then depends only on the sources, which reproducible builds
            // (F-Droid rebuilds the fdroid flavor and compares it) need.
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    hilt {
        enableAggregatingTask = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        // Actually gate CI on lint: fail the build on errors. Pre-existing issues are
        // captured in lint-baseline.xml so only newly introduced ones break the build
        // (mirrors the detekt new-code baseline approach).
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        // Severities (i18n, unused resources, security checks as errors) live in lint.xml.
        lintConfig = file("lint.xml")
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    ksp {
        // Exported Room schemas are committed (app/schemas) so every schema change is reviewed
        // and MigrationTestHelper can validate upgrades in androidTest.
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

// Baseline (ART) profiles are generated non-deterministically; F-Droid rebuilds the fdroid
// flavor and compares it byte-for-byte, so the fdroid variants ship without one.
tasks.configureEach {
    if (name.contains("Fdroid") && name.contains("ArtProfile")) {
        enabled = false
    }
}

// StoreMetadataTest and CodeQualityToolingTest read files outside the module; declare them as
// inputs so an edited store text, changelog, workflow or tool config re-runs the unit tests
// instead of leaving them UP-TO-DATE.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("fastlane/metadata/android"))
        .withPropertyName("storeMetadata")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        rootProject.file("CHANGELOG.md"),
        rootProject.file("version.properties"),
        rootProject.file(".github/workflows/publish-play.yml"),
    ).withPropertyName("releaseFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    // CodeQualityToolingTest reads the lint/detekt/CI configuration and runs the git hooks.
    inputs.dir(rootProject.file(".githooks"))
        .withPropertyName("gitHooks")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        rootProject.file(".github/workflows/android.yml"),
        rootProject.file("config/detekt/detekt.yml"),
        file("lint.xml"),
    ).withPropertyName("qualityConfig").withPathSensitivity(PathSensitivity.RELATIVE)
}

// Coverage: `./gradlew :app:koverXmlReportFdroidDebug :app:koverVerifyFdroidDebug`.
// The gate is scoped to core/security, the code that guards the key hierarchy.
kover {
    reports {
        variant("fdroidDebug") {
            filters {
                includes {
                    packages("com.pasich.encly.core.security")
                }
            }
            verify {
                rule("core/security line coverage") {
                    minBound(koverMinSecurityLineCoverage)
                }
            }
        }
    }
}

dependencies {

    // Jetpack Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ProcessLifecycleOwner for app foreground/background detection (auto re-lock).
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    // Per-app language (AppCompatDelegate.setApplicationLocales) on every supported API level.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    // Drag and Drop
    implementation(libs.reorderable)

    // Room
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.core.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Biometric Authentication
    implementation(libs.androidx.biometric)

    // Dagger Hilt - Use KSP consistently
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Other dependencies
    implementation(libs.coil.compose)
    implementation(libs.gson)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Cipher
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.kotlin.bip39)

    // Security Crypto (see the note in gradle/libs.versions.toml before replacing it)
    implementation(libs.androidx.security.crypto)

    implementation(libs.icons.lucide)

    // detekt rule set for Jetpack Compose (Apache-2.0); runs only inside the detekt task.
    detektPlugins(libs.compose.rules.detekt)

    // Тести
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: native SQLCipher open/lock under WAL and the exported Room schema.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
