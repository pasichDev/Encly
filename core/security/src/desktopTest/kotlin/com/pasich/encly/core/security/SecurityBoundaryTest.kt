package com.pasich.encly.core.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The security core is common code: its shared sources may import nothing of Android or the JVM, and nothing of
 * the app (the database, the backup manager, resources, the DI graph). What a platform has to do is behind an
 * interface the core declares (KeyValueStore, VaultFileIo, VaultDatabase, PinHardwareFactor, BiometricManager,
 * LockoutClock, DeviceLockWatcher, MainThreadTimer, HandoffStagingSweeper) and implemented in `:app`, except
 * the two files in [JVM_FILES], which both JVM targets share and which are the only source files that may
 * name `java.io`. The module may depend on `:core:common` and `:core:crypto` only.
 */
class SecurityBoundaryTest {
    // Tests run with the module directory as the working directory.
    private val moduleRoot = listOf(File("."), File("core/security")).first { File(it, "build.gradle.kts").isFile }
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
        assertTrue(sources.size >= 25, "no sources under $sourceRoot")
    }

    @Test
    fun noImportReachesOutOfTheModuleOrOntoThePlatform() {
        val offenders = sources.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.relative()}:$number import $import".takeUnless { ALLOWED_PREFIXES.any(import::startsWith) }
            }
        }
        assertEquals(emptyList(), offenders, "move the platform call behind an interface and an adapter")
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = sources.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals(emptyList(), offenders, "the shared sources may not use the JVM or Android directly")
    }

    @Test
    fun onlyTwoJvmFilesTouchTheJvm() {
        val platformSets = File(moduleRoot, "src").listFiles().orEmpty().map { it.name }.filter { it.endsWith("Main") }
        assertEquals(listOf("commonMain", "jvmAndAndroidMain"), platformSets.sorted())
        val jvmFiles = File(moduleRoot, "src/jvmAndAndroidMain/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.map { it.name }.toSet()
        assertEquals(JVM_FILES.keys, jvmFiles)
        JVM_FILES.forEach { (name, reason) -> assertTrue(reason.length > 10, "$name: write why it is JVM code") }
    }

    @Test
    fun theModuleDependsOnlyOnCommonAndCrypto() {
        val build = File(moduleRoot, "build.gradle.kts").readText()
        val projects = Regex("""project\(\s*"(:[\w:]+)"""").findAll(build).map { it.groupValues[1] }.toList()
        assertEquals(listOf(":core:common", ":core:crypto"), projects)
    }

    @Test
    fun theCheckSeesWhatItShouldReject() {
        assertTrue(ALLOWED_PREFIXES.none { "android.content.Context".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "java.io.File".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "javax.crypto.Cipher".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "androidx.fragment.app.FragmentActivity".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "androidx.compose.ui.res.stringResource".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "androidx.room.Dao".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.R".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.data.database.VaultFile".startsWith(it) })
        assertTrue(ALLOWED_PREFIXES.none { "com.pasich.encly.core.di.ApplicationContext".startsWith(it) })
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

        // The module's own packages, the common and crypto layers it stands on, Metro's annotations, the
        // androidx annotations and lifecycle observer, and the Kotlin libraries.
        val ALLOWED_PREFIXES = listOf(
            "com.pasich.encly.core.security.",
            "com.pasich.encly.core.backup.",
            "com.pasich.encly.core.crypto.",
            "com.pasich.encly.core.common.",
            "com.pasich.encly.core.platform.",
            "com.pasich.encly.core.AppLogger",
            "dev.zacsweers.metro.",
            "androidx.annotation.",
            "androidx.lifecycle.",
            "kotlin.",
            "kotlinx.",
        )

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

        /** The JVM and Android actuals, with why each is not common code. */
        val JVM_FILES = mapOf(
            "JvmVaultFileIo.kt" to "java.io and java.nio: the vault file's fsync and atomic replace",
            "JvmJsonBytes.kt" to "java.io byte-array streams so the plaintext payload can be wiped",
        )
    }
}
