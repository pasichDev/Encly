package com.pasich.encly

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `:shared` is the app without Android: its common sources (the domain, the data logic, the UI) may import
 * nothing of Android or the JVM and name none of the Android-bound Compose locals, and the module may not
 * depend on `:app`. What the UI needs from a platform is an interface in `com.pasich.encly.platform` that the
 * host implements. The platform source sets are an inventory: each file in [JVM_FILES], [ANDROID_FILES] and
 * [DESKTOP_FILES] is named with the reason it is not common code, and nothing else may be there.
 */
class SharedBoundaryTest {
    // Tests run with the module directory as the working directory.
    private val moduleRoot = listOf(File("."), File("shared")).first { File(it, "build.gradle.kts").isFile }
    private val sourceRoot = File(moduleRoot, "src/commonMain/kotlin")

    private val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.relative() = relativeTo(sourceRoot).invariantSeparatorsPath

    /** Source lines without comments (line, block and KDoc) and string literals' contents. */
    private fun File.codeLines(): List<Pair<Int, String>> {
        // A block comment becomes as many line breaks as it had, so line numbers stay right.
        val text = BLOCK_COMMENT.replace(readText()) { "\n".repeat(it.value.count { c -> c == '\n' }) }
        return text.lines().mapIndexed { index, raw ->
            (index + 1) to raw.replace(STRING, "\"\"").substringBefore("//")
        }
    }

    private fun forbidden(import: String): Boolean = FORBIDDEN_PREFIXES.any(import::startsWith)

    private fun platformFiles(sourceSet: String): Set<String> {
        val dir = File(moduleRoot, "src/$sourceSet/kotlin")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()
    }

    @Test
    fun theSourcesWereFound() {
        assertTrue(sources.size > 200, "no sources under $sourceRoot")
    }

    @Test
    fun noImportReachesOntoAndroidOrTheJvm() {
        val offenders = sources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.relative()}:$number import $import".takeIf { forbidden(import) }
            }
        }
        assertEquals(emptyList(), offenders, "put the platform call behind an interface the host implements")
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals(emptyList(), offenders, "the common sources may not use the JVM or Android directly")
    }

    @Test
    fun theWindowAndContextLocalsAreNotUsed() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> ANDROID_LOCALS.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals(emptyList(), offenders, "use the host services of com.pasich.encly.platform instead")
    }

    @Test
    fun theJvmAndPlatformSourceSetsHoldOnlyTheListedFiles() {
        assertEquals(JVM_FILES.keys, platformFiles("jvmAndAndroidMain"))
        assertEquals(ANDROID_FILES.keys, platformFiles("androidMain"))
        assertEquals(DESKTOP_FILES.keys, platformFiles("desktopMain"))
        (JVM_FILES + ANDROID_FILES + DESKTOP_FILES).forEach { (name, reason) ->
            assertTrue(reason.length > 10, "$name: write why it is platform code")
        }
    }

    @Test
    fun theModuleDoesNotDependOnTheApp() {
        val build = File(moduleRoot, "build.gradle.kts").readText()
        val projects = Regex("""project\(\s*"(:[\w:]+)"""").findAll(build).map { it.groupValues[1] }.toList()
        assertTrue(projects.isNotEmpty())
        assertFalse(":app" in projects, "the app depends on :shared, not the other way round")
    }

    @Test
    fun theCheckSeesWhatItShouldReject() {
        assertTrue(forbidden("android.content.Context"))
        assertTrue(forbidden("java.io.File"))
        assertTrue(forbidden("javax.crypto.Cipher"))
        assertTrue(forbidden("androidx.fragment.app.FragmentActivity"))
        assertTrue(forbidden("androidx.activity.compose.BackHandler"))
        assertTrue(forbidden("androidx.core.net.toUri"))
        assertTrue(forbidden("androidx.compose.ui.platform.LocalContext"))
        assertTrue(forbidden("com.pasich.encly.R"))
        assertTrue(forbidden("com.pasich.encly.BuildConfig"))
        assertTrue(forbidden("com.pasich.encly.core.di.ApplicationContext"))
        assertFalse(forbidden("androidx.compose.ui.backhandler.BackHandler"))
        assertFalse(forbidden("androidx.lifecycle.ViewModel"))
        assertFalse(forbidden("androidx.navigation.NavHostController"))
        assertFalse(forbidden("kotlinx.io.files.Path"))
        assertTrue(PLATFORM_USE.containsMatchIn("val x = java.util.UUID.randomUUID()"))
        assertTrue(PLATFORM_USE.containsMatchIn("val t = System.currentTimeMillis()"))
        assertTrue(PLATFORM_USE.containsMatchIn("} catch (_: SecurityException) {"))
        assertFalse(PLATFORM_USE.containsMatchIn("val t = Clock.System.now()"))
        assertTrue(PLATFORM_USE.containsMatchIn("e.javaClass.simpleName"))
        assertTrue(PLATFORM_USE.containsMatchIn("} catch (_: OutOfMemoryError) {"))
        assertTrue(ANDROID_LOCALS.containsMatchIn("val context = LocalContext.current"))
        assertTrue(ANDROID_LOCALS.containsMatchIn("val activity = LocalActivity.current"))
        assertFalse(ANDROID_LOCALS.containsMatchIn("val window = LocalAppWindow.current"))
    }

    private companion object {
        val IMPORT = Regex("""import ([\w.]+)(?: as \w+)?""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")

        val FORBIDDEN_PREFIXES = listOf(
            "android.",
            "java.",
            "javax.",
            "dagger.",
            "androidx.activity.",
            "androidx.core.",
            "androidx.appcompat.",
            "androidx.fragment.",
            "androidx.biometric.",
            "androidx.compose.ui.platform.LocalContext",
            "androidx.compose.ui.platform.LocalView",
            "androidx.compose.ui.platform.LocalConfiguration",
            "androidx.compose.ui.platform.LocalResources",
            "androidx.compose.ui.res.",
            "com.pasich.encly.R",
            "com.pasich.encly.BuildConfig",
            "com.pasich.encly.core.di.ApplicationContext",
        )

        // Direct uses of the platform that need no import: FQNs, System, javaClass, the JDK classes that
        // need no import (Character) or whose import the check would also catch (Normalizer).
        val PLATFORM_USE = Regex(
            listOf(
                """\b(?:java|javax|android)\.\w""",
                """(?<!Clock\.)\bSystem\.""",
                """\bSystem::""",
                """\.javaClass\b""",
                """::class\.java\b""",
                """\bSecurityException\b""",
                """\bCharacter\.""",
                """\bNormalizer\.""",
                """\bOutOfMemoryError\b""",
                """\bStackOverflowError\b""",
            ).joinToString("|"),
        )

        // The Compose locals that are the Android window and Context in disguise.
        val ANDROID_LOCALS = Regex("""\b(?:LocalContext|LocalActivity|LocalView|LocalConfiguration|LocalResources)\b""")

        /** Code both JVM targets share (Android and desktop), with why it is not common code. */
        val JVM_FILES = mapOf(
            "com/pasich/encly/data/zip/JvmZip.kt" to "java.util.zip: CRC-32, inflater, ZipFile, ZipOutputStream",
            "com/pasich/encly/data/export/JvmUnicode.kt" to
                "java.text.Normalizer and Character categories for file names",
            "com/pasich/encly/utils/LocalizedDateJvm.kt" to "java.text date formats, the same on both JVM targets",
            "com/pasich/encly/data/importer/OutOfMemory.kt" to "OutOfMemoryError, a JVM class with no common name",
        )

        /** The Android actuals. */
        val ANDROID_FILES = mapOf(
            "com/pasich/encly/utils/LocalizedDate.android.kt" to
                "the app locale of the Resources and the best date pattern",
            "com/pasich/encly/ui/theme/WallpaperColors.android.kt" to
                "Material You colours from the wallpaper (Android 12+)",
            "com/pasich/encly/presentation/effects/AnimationSettings.android.kt" to
                "the system animator duration scale",
            "com/pasich/encly/presentation/effects/WindowMetrics.android.kt" to
                "the IME inset and the configuration width",
        )

        /** The desktop actuals. */
        val DESKTOP_FILES = mapOf(
            "com/pasich/encly/utils/LocalizedDate.desktop.kt" to "the JVM default locale and a fixed day-month pattern",
            "com/pasich/encly/ui/theme/WallpaperColors.desktop.kt" to "no wallpaper colours on the desktop",
            "com/pasich/encly/presentation/effects/AnimationSettings.desktop.kt" to
                "no animation setting on the desktop",
            "com/pasich/encly/presentation/effects/WindowMetrics.desktop.kt" to
                "the IME inset and the window size on the desktop",
        )
    }
}
