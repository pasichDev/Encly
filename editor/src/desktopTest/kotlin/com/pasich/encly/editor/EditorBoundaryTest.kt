package com.pasich.encly.editor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The editor is a library of its own, so its shared sources may depend on nothing of the app and
 * nothing of the platform: no `com.pasich.encly.` import outside `com.pasich.encly.editor`, no
 * `android.`, `java.` or `javax.`, no `R`. Compose and `kotlin`/`kotlinx` libraries are what it may
 * use. Platform code lives in the `androidMain`, `desktopMain` and `iosMain` source sets, behind
 * `expect` declarations. The module may not depend on the app either (see [theModuleDoesNotDependOnTheApp]).
 */
class EditorBoundaryTest {
    // Tests run with the module directory as the working directory.
    private val moduleRoot = listOf(File("."), File("editor")).first { File(it, "build.gradle.kts").isFile }
    private val sourceRoot = File(moduleRoot, "src/commonMain/kotlin/com/pasich/encly/editor")

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
        assertTrue(sources.size > 50, "no sources under $sourceRoot")
    }

    @Test
    fun noImportReachesOutOfTheEditorOrOntoThePlatform() {
        val offenders = sources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.relative()}:$number import $import".takeUnless { ALLOWED_PREFIXES.any(import::startsWith) }
            }
        }
        assertEquals(emptyList(), offenders, "the editor may not import these")
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals(emptyList(), offenders, "the editor may not use the JVM or Android directly")
    }

    @Test
    fun theModuleDoesNotDependOnTheApp() {
        val build = File(moduleRoot, "build.gradle.kts").readText()
        assertFalse(Regex("""project\(\s*":(?!editor)""").containsMatchIn(build), "the editor depends on a module")
    }

    @Test
    fun theCheckSeesWhatItShouldReject() {
        assertFalse(IMPORT.matchEntire("import java.net.IDN") == null)
        assertTrue(PLATFORM_USE.containsMatchIn("val x = java.util.UUID.randomUUID()"))
        assertTrue(PLATFORM_USE.containsMatchIn("val t = System.currentTimeMillis()"))
        assertFalse(PLATFORM_USE.containsMatchIn("val t = Clock.System.now()"))
        assertTrue(PLATFORM_USE.containsMatchIn("e.javaClass.simpleName"))
        assertFalse(PLATFORM_USE.containsMatchIn("val language = Locale.javascript"))
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.R".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.presentation.designsystem.X".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "androidx.activity.compose.X".startsWith(it) })
    }

    private companion object {
        val IMPORT = Regex("""import ([\w.]+)(?: as \w+)?""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")

        // Compose and the Kotlin libraries; the editor's own package.
        val ALLOWED_PREFIXES = listOf(
            "com.pasich.encly.editor.",
            "androidx.compose.",
            "org.jetbrains.compose.",
            "kotlin.",
            "kotlinx.",
        )

        // Direct uses of the platform that need no import: java.*, android.*, System, javaClass, R.
        val PLATFORM_USE = Regex(
            listOf(
                """\b(?:java|javax|android)\.\w""",
                """(?<!Clock\.)\bSystem\.""",
                """\.javaClass\b""",
                """::class\.java\b""",
                """\bR\.(?:string|drawable|plurals)\b""",
            ).joinToString("|"),
        )
    }
}
