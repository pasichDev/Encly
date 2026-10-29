package com.pasich.encly.core.crypto

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The crypto API (`Crypto`) and BIP-39 are common code: the shared sources may import nothing of Android or
 * the JVM, and nothing of the app or of a module above `:core:common`. The one place that may name `javax.crypto`
 * and `java.security` is the JCA actual in `jvmAndAndroidMain`, which is checked to be the only platform source
 * set. The module may depend on `:core:common` and no other module (see [theModuleDependsOnlyOnCommon]).
 */
class CryptoBoundaryTest {
    // Tests run with the module directory as the working directory.
    private val moduleRoot = listOf(File("."), File("core/crypto")).first { File(it, "build.gradle.kts").isFile }
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
        assertTrue(sources.size >= 6, "no sources under $sourceRoot")
    }

    @Test
    fun noImportReachesOutOfTheModuleOrOntoThePlatform() {
        val offenders = sources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.relative()}:$number import $import".takeUnless { ALLOWED_PREFIXES.any(import::startsWith) }
            }
        }
        assertEquals(emptyList(), offenders, "the shared crypto sources may not import these")
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals(emptyList(), offenders, "the shared crypto sources may not use the JVM or Android directly")
    }

    @Test
    fun onlyTheJvmSourceSetHoldsPlatformCode() {
        val platformSets = File(moduleRoot, "src").listFiles().orEmpty().map { it.name }.filter { it.endsWith("Main") }
        assertEquals(listOf("commonMain", "iosMain", "jvmAndAndroidMain"), platformSets.sorted())
    }

    @Test
    fun theModuleDependsOnlyOnCommon() {
        val build = File(moduleRoot, "build.gradle.kts").readText()
        val projects = Regex("""project\(\s*"(:[\w:]+)"""").findAll(build).map { it.groupValues[1] }.toList()
        assertEquals(listOf(":core:common"), projects)
    }

    @Test
    fun theIosSourcesImportOnlyAppleLibrariesAndTheModulesOwnCode() {
        val iosSources = File(moduleRoot, "src/iosMain/kotlin").walkTopDown().filter {
            it.isFile && it.extension == "kt"
        }
        val offenders = iosSources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.name}:$number import $import".takeUnless { IOS_ALLOWED_PREFIXES.any(import::startsWith) }
            }
        }.toList()
        assertEquals(emptyList(), offenders, "the iOS sources may import Apple libraries and the module itself")
    }

    @Test
    fun theCheckSeesWhatItShouldReject() {
        assertTrue(ALLOWED_PREFIXES.none { "javax.crypto.Cipher".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "java.security.MessageDigest".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "android.os.Build".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.core.security.SecurityManager".startsWith(it) })
        assertTrue(PLATFORM_USE.containsMatchIn("val x = java.util.UUID.randomUUID()"))
        assertTrue(PLATFORM_USE.containsMatchIn("val t = System.currentTimeMillis()"))
        assertFalse(PLATFORM_USE.containsMatchIn("val t = Clock.System.now()"))
        assertTrue(PLATFORM_USE.containsMatchIn("e.javaClass.simpleName"))
    }

    private companion object {
        val IMPORT = Regex("""import ([\w.]+)(?: as \w+)?""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")

        val ALLOWED_PREFIXES = listOf(
            "com.pasich.encly.core.crypto.",
            "com.pasich.encly.core.security.bip39.",
            "com.pasich.encly.core.security.SensitiveDataCleaner",
            "kotlin.",
            "kotlinx.",
        )

        val IOS_ALLOWED_PREFIXES = ALLOWED_PREFIXES + listOf("platform.", "kotlinx.cinterop.")

        // Direct uses of the platform that need no import: java.*, android.*, System, javaClass.
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
            ).joinToString("|"),
        )
    }
}
