// The encrypted vault database as a Kotlin Multiplatform library (Android, desktop JVM, iOS): the Room
// schema, the entities and DAOs, the migrations and SecureDatabaseManager, which opens the database
// through Sealite (SQLCipher). The app supplies where the files are through DatabasePlatform.
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension

plugins {
    id("encly.kmp-library")
    id("encly.quality")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.metro)
}

// The shared detekt config plus the module's own (the tests are left out of the style and complexity rules).
// Findings that predate the move out of the app are captured in detekt-baseline.xml, which should only
// ever shrink.
detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"), files("config/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

// Minimum line coverage (%) of the whole module enforced by koverVerify.
val databaseMinCoverage = 85

kotlin {
    android {
        namespace = "com.pasich.encly.core.database"
        // Android resources (and so assets) are off in a Kotlin Multiplatform library unless asked for; the
        // device tests need the exported schemas as assets of their app.
        androidResources {
            enable = true
        }
        // Instrumented tests (native SQLCipher on a device, every schema migration): task
        // `connectedAndroidDeviceTest`, in the source set `androidDeviceTest`.
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // RoomDatabase and the DAOs' Flow are part of the public API, and so is kotlinx-io's Path.
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)
            // The cipher: SQLCipher through the androidx.sqlite driver API, on every target.
            implementation(libs.sealite)
            // SynchronizedObject: the lock of SecureDatabaseManager that @Synchronized would not give iOS.
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.mockito.core)
            implementation(libs.robolectric)
            // Room's migrations run against the platform SQLite under Robolectric.
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.sqlite.framework)
            // Writes a vault the way earlier releases did, to check that the current driver opens it.
            implementation(libs.sqlcipher.android)
        }
    }
}

// Room's compiler runs once per target (KSP), and writes the database implementation and the actual of
// AppDatabaseConstructor for it. The configurations are added as KSP creates them.
val roomCompilerTargets = setOf("kspAndroid", "kspDesktop", "kspIosArm64", "kspIosSimulatorArm64")
configurations.configureEach {
    if (name in roomCompilerTargets) {
        project.dependencies.add(name, libs.androidx.room.compiler)
    }
}

// The exported schemas (core/database/schemas) are committed, so every schema change is reviewed and the
// migration tests can validate upgrades against them.
room {
    schemaDirectory("$projectDir/schemas")
}

// The device tests' MigrationTestHelper reads the exported schemas as assets of the test app, at the
// assets root (<schemas>/<database class>/<version>.json), as the app's androidTest did.
extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>().onVariants { variant ->
    variant.deviceTests.values.forEach { deviceTest ->
        deviceTest.sources.assets?.addStaticSourceDirectory(layout.projectDirectory.dir("schemas").asFile.path)
    }
}

tasks.withType<Test>().configureEach {
    // Sealite loads its native library into the test JVM; be explicit about native access (JDK 24+ warns).
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// Coverage: `./gradlew :core:database:koverVerify`, over the desktop JVM tests and the Android host tests.
kover {
    reports {
        total {
            filters {
                // Code the compilers and annotation processors write, not this project.
                excludes {
                    classes(
                        "*_Impl",
                        "*_Impl$*",
                        "*AppDatabaseConstructor*",
                        "*MetroFactory*",
                        "*MetroContributionTo*",
                    )
                }
            }
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Database line coverage") {
                    minBound(databaseMinCoverage)
                }
            }
        }
    }
}
