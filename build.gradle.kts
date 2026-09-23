// Plugin versions live in gradle/libs.versions.toml (one Kotlin version for every Kotlin plugin).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

// Formatting: `./gradlew spotlessCheck` (CI, pre-commit hook) / `./gradlew spotlessApply`.
// ktlint reads its rules from .editorconfig, so the IDE and the build format the same way.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    // Declared explicitly so an .editorconfig change invalidates Spotless's up-to-date cache.
    val editorConfig = rootProject.file(".editorconfig")
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
    }
}

// Opt-in git hooks (.githooks/): pre-commit runs spotlessCheck + detekt when Kotlin/Gradle files
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
