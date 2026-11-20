package com.pasich.encly.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `:core:common` is the bottom of the module graph and runs on every target, so its shared sources may
 * depend on nothing of the app, of another module, of the JVM or of Android: no `android.`, `java.` or
 * `javax.` import, no `R`, no `System`, `javaClass` or `SecurityException` in code. The libraries it may
 * use are `kotlin` and `kotlinx`. The Android implementations of its interfaces
 * are in `:app`. The module may not depend on another module either (see [theModuleDependsOnNoModule]).
 */
class CommonBoundaryTest {
    // Tests run with the module directory as the working directory.
    private val moduleRoot = listOf(File("."), File("core/common")).first { File(it, "build.gradle.kts").isFile }
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

    @Test
    fun theSourcesWereFound() {
        assertTrue(sources.size >= 8, "no sources under $sourceRoot")
    }

    @Test
    fun noImportReachesOutOfTheModuleOrOntoThePlatform() {
        val offenders = sources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.relative()}:$number import $import".takeUnless { ALLOWED_PREFIXES.any(import::startsWith) }
            }
        }
        assertEquals(emptyList(), offenders, "the common layer may not import these")
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals(emptyList(), offenders, "the common layer may not use the JVM or Android directly")
    }

    @Test
    fun theModuleDependsOnNoModule() {
        val build = File(moduleRoot, "build.gradle.kts").readText()
        assertFalse(Regex("""project\(\s*":""").containsMatchIn(build), "the common layer depends on a module")
    }

    @Test
    fun theCheckSeesWhatItShouldReject() {
        assertTrue(ALLOWED_PREFIXES.none { "android.content.Context".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "java.io.File".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "javax.crypto.Cipher".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "androidx.fragment.app.FragmentActivity".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.R".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "androidx.compose.ui.res.stringResource".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.core.security.Crypto".startsWith(it) })
        assertTrue(PLATFORM_USE.containsMatchIn("val x = java.util.UUID.randomUUID()"))
        assertTrue(PLATFORM_USE.containsMatchIn("val t = System.currentTimeMillis()"))
        assertTrue(PLATFORM_USE.containsMatchIn("} catch (_: SecurityException) {"))
        assertFalse(PLATFORM_USE.containsMatchIn("val t = Clock.System.now()"))
        assertTrue(PLATFORM_USE.containsMatchIn("e.javaClass.simpleName"))
        assertFalse(PLATFORM_USE.containsMatchIn("val language = Locale.javascript"))
    }

    private companion object {
        val IMPORT = Regex("""import ([\w.]+)(?: as \w+)?""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")

        // The Kotlin libraries and the module's own packages (the sensitive-data wipe
        // lives in core.security for the packages' sake, nothing else of core.security is allowed here).
        val ALLOWED_PREFIXES = listOf(
            "com.pasich.encly.core.common.",
            "com.pasich.encly.core.platform.",
            "com.pasich.encly.core.AppLogger",
            "com.pasich.encly.core.security.SensitiveDataCleaner",
            "kotlin.",
            "kotlinx.",
            // The type of a string resource, which UiText and AppStrings carry; the strings are not here.
            "org.jetbrains.compose.resources.",
        )

        // Direct uses of the platform that need no import: java.*, android.*, System, javaClass, R.
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
                """\bR\.(?:string|drawable|plurals)\b""",
            ).joinToString("|"),
        )
    }
}
