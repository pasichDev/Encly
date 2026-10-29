// Plugin versions live in gradle/libs.versions.toml (one Kotlin version for every Kotlin plugin).
// The convention plugins in build-logic/ apply AGP, Kotlin Multiplatform, Compose, detekt, Kover and
// Dokka for the library modules; the app still declares its own plugins below.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.metro) apply false
    // Formatting for the whole repository (ktlint through Spotless): `./gradlew spotlessCheck` (CI,
    // pre-commit hook) / `./gradlew spotlessApply`. Defined in build-logic (ktlint reads .editorconfig).
    id("encly.spotless")
}

// Opt-in hooks (.githooks/): pre-commit runs spotlessCheck + detekt when Kotlin/Gradle files
// are staged, commit-msg enforces Conventional Commits. Same as `git config core.hooksPath .githooks`.
tasks.register<Exec>("installGitHooks") {
    group = "help"
    description = "Points this clone's git hooks at .githooks/ (git config core.hooksPath .githooks)."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}

// Spotless applies the `base` plugin, which already provides `clean`; extend it.
tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.get().asFile)
}
