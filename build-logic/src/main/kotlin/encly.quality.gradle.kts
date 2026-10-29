// Convention plugin `encly.quality`: static analysis and coverage for a module. detekt runs the
// repository's config/detekt/detekt.yml (default rules plus the Compose rules); Kover measures the
// module, and the module declares its own coverage gates. Formatting is `encly.spotless` at the root.

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

// detekt looks at src/main only; a multiplatform module keeps its code in src/<source set>/kotlin.
pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    detekt {
        source.setFrom(
            fileTree("src") {
                include("*/kotlin/**/*.kt")
            },
        )
    }
}

dependencies {
    // detekt rule set for Jetpack Compose (Apache-2.0); runs only inside the detekt task.
    add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())
}
