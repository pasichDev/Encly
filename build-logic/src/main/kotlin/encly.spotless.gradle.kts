// Convention plugin `encly.spotless`: ktlint formatting of every Kotlin source and Gradle script of the
// repository. Apply it to the root project only: `./gradlew spotlessCheck` / `spotlessApply`.

plugins {
    id("com.diffplug.spotless")
}

val ktlintVersion =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("ktlint")
        .get()
        .requiredVersion

spotless {
    // ktlint reads its rules from .editorconfig, so the IDE and the build format the same way. It is
    // declared explicitly so that an .editorconfig change invalidates Spotless's up-to-date cache.
    val editorConfig = rootProject.file(".editorconfig")
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".claude/**")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".claude/**")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
    }
}
