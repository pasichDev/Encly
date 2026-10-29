import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// The block editor as a Kotlin Multiplatform library (Android, desktop JVM, iOS). It depends on nothing
// of Encly: the app binds its extension points (strings, theme, clipboard, links) from outside.
plugins {
    id("encly.kmp-library")
    id("encly.compose")
    id("encly.quality")
    id("org.jetbrains.dokka")
}

// The shared detekt config plus the module's own (KDoc on every public declaration). Findings that
// predate the move out of the app are captured in detekt-baseline.xml, which should only ever shrink.
detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"), files("config/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

// Minimum line coverage (%) of the whole module and branch coverage (%) of its logic packages, both
// enforced by koverVerify.
val editorMinCoverage = 100

kotlin {
    android {
        namespace = "com.pasich.encly.editor"
    }

    sourceSets {
        commonMain.dependencies {
            // StateFlow and Flow are part of the public API.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            // Compose UI tests run on the JVM under Robolectric, no emulator needed.
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.mockito.core)
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

// Coverage: `./gradlew :editor:koverVerify`. Every line of the module is covered by its own tests, and
// so is every branch of its logic (model, codec, state, focus, theme). The branches of @Composable
// functions are not gated: the Compose compiler adds `$changed`/`$default` checks to every one of them,
// which no test can take both ways.
kover {
    // The same classes and tests as the total report (the desktop JVM tests and the Android host tests),
    // for the branch gate of the logic packages below.
    currentProject {
        createVariant("editorLogic") {
            add("desktop")
            add("android")
        }
    }
    reports {
        total {
            filters {
                excludes {
                    classes("*ComposableSingletons*")
                }
            }
            xml { onCheck.set(false) }
            html { onCheck.set(false) }
            verify {
                rule("Editor line coverage") {
                    bound {
                        minValue = editorMinCoverage
                        coverageUnits = CoverageUnit.LINE
                    }
                }
            }
        }
        variant("editorLogic") {
            filters {
                includes {
                    packages(
                        "com.pasich.encly.editor.model",
                        "com.pasich.encly.editor.codec",
                        "com.pasich.encly.editor.state",
                        "com.pasich.encly.editor.focus",
                        "com.pasich.encly.editor.theme",
                    )
                }
            }
            verify {
                rule("Editor logic branch coverage") {
                    bound {
                        minValue = editorMinCoverage
                        coverageUnits = CoverageUnit.BRANCH
                    }
                }
            }
        }
    }
}

dokka {
    moduleName.set("Encly editor")
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
    }
    // A KDoc link that does not resolve fails the build instead of rotting in the published docs.
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
}

// Dokka 2.2 cannot be stored in the configuration cache that this build turns on ("circular evaluation"
// of its plugin parameters); the Dokka tasks run without it.
tasks.matching { it.name.startsWith("dokkaGenerate") }.configureEach {
    notCompatibleWithConfigurationCache("Dokka 2.2 plugin parameters cannot be serialized")
}

// Generating the docs is part of `check`, so a broken KDoc link or Module.md fails it.
tasks.named("check") {
    dependsOn("dokkaGeneratePublicationHtml")
}
