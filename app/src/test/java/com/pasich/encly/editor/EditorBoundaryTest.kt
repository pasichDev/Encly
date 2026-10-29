package com.pasich.encly.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The editor package is written to move into a Kotlin Multiplatform library of its own, so its
 * sources may depend on nothing of the app and nothing of the platform: no `com.pasich.encly.`
 * import outside `com.pasich.encly.editor`, no `android.`, `java.` or `javax.`, no `R`. Compose and
 * `kotlin`/`kotlinx` libraries are what it may use. A source that still needs the JVM or Android
 * is listed in [ALLOWED_PLATFORM_FILES] with the reason, and nothing else may be.
 */
class EditorBoundaryTest {
    private val sourceRoot = listOf(
        File("src/main/java/com/pasich/encly/editor"),
        File("app/src/main/java/com/pasich/encly/editor"),
    ).first { it.isDirectory }

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
        assertTrue("no sources under $sourceRoot", sources.size > 50)
    }

    @Test
    fun noImportReachesOutOfTheEditorOrOntoThePlatform() {
        val offenders = sources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                val allowed = ALLOWED_PREFIXES.any { import.startsWith(it) } ||
                    import in ALLOWED_PLATFORM_FILES[file.relative()].orEmpty()
                "${file.relative()}:$number import $import".takeUnless { allowed }
            }
        }
        assertEquals("the editor may not import these", emptyList<String>(), offenders)
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals("the editor may not use the JVM or Android directly", emptyList<String>(), offenders)
    }

    @Test
    fun theAllowListHoldsOnlyWhatIsStillNeeded() {
        ALLOWED_PLATFORM_FILES.forEach { (path, imports) ->
            val file = File(sourceRoot, path)
            assertTrue("$path is listed but gone", file.isFile)
            val text = file.readText()
            imports.forEach { assertTrue("$path no longer imports $it", "import $it" in text) }
        }
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
    }

    private companion object {
        val IMPORT = Regex("""import ([\w.]+)(?: as \w+)?""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")

        // Compose and the Kotlin libraries; the editor's own package.
        val ALLOWED_PREFIXES = listOf(
            "com.pasich.encly.editor.",
            "androidx.compose.",
            "androidx.lifecycle.",
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

        /**
         * Platform-specific sources, with the imports they are allowed (and why). Each is one tiny
         * file that becomes an `expect`/`actual` pair when the editor moves to a multiplatform module.
         */
        val ALLOWED_PLATFORM_FILES = mapOf(
            // Punycode for the link card's host (see the file's comment).
            "ui/blocks/IdnShim.kt" to setOf("java.net.IDN"),
        )
    }
}
