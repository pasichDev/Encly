package com.pasich.encly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The domain and data logic is written to move into Kotlin Multiplatform modules (`:core:data`,
 * `:core:security`, `:core:database`), so the packages in [LOGIC_PACKAGES] may use nothing of
 * Android and nothing of the JVM: no `android.` or `javax.` or `java.` import, no `androidx.`
 * beyond Room, sqlite, DataStore, lifecycle and annotations, no `System`, `javaClass` or
 * `SecurityException` in code (`R.string` ids in domain enums stay until Compose
 * resources replace them). What the platform has to do is behind an interface the
 * logic declares (KeyValueStore, VaultFileIo, BiometricManager, MainThreadTimer, AppStrings,
 * LockoutClock, ...) and implemented in one adapter file. Each adapter file is listed in
 * [ADAPTER_FILES] with the reason, and nothing else may be: the check also fails when a listed
 * file no longer needs its place, and when a logic file names a type that only an adapter
 * declares (a same-package reference needs no import, so imports alone would not show it).
 */
class PlatformBoundaryTest {
    private val sourceRoot = listOf(
        File("src/main/java/com/pasich/encly"),
        File("app/src/main/java/com/pasich/encly"),
    ).first { it.isDirectory }

    private fun File.relative() = relativeTo(sourceRoot).invariantSeparatorsPath

    private val logicFiles: List<File> = LOGIC_PACKAGES.flatMap { pkg ->
        File(sourceRoot, pkg).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private val checkedFiles = logicFiles.filter { it.relative() !in ADAPTER_FILES }

    /** Source lines without comments (line, block and KDoc) and string literals' contents. */
    private fun File.codeLines(): List<Pair<Int, String>> {
        // A block comment becomes as many line breaks as it had, so line numbers stay right.
        val text = BLOCK_COMMENT.replace(readText()) { "\n".repeat(it.value.count { c -> c == '\n' }) }
        return text.lines().mapIndexed { index, raw ->
            (index + 1) to raw.replace(STRING, "\"\"").substringBefore("//")
        }
    }

    private fun forbidden(import: String): Boolean = when {
        ALLOWED_ANDROIDX.any { import.startsWith(it) } -> false
        else -> FORBIDDEN_PREFIXES.any { import.startsWith(it) }
    }

    @Test
    fun theSourcesWereFound() {
        assertTrue("no logic sources under $sourceRoot", logicFiles.size > 80)
        assertTrue(ADAPTER_FILES.keys.all { File(sourceRoot, it).isFile })
    }

    @Test
    fun noImportReachesOntoAndroidOrTheJvm() {
        val offenders = checkedFiles.flatMap { file ->
            file.codeLines().mapNotNull { (number, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                "${file.relative()}:$number import $import".takeIf { forbidden(import) }
            }
        }
        assertEquals("move the platform call behind an interface and an adapter file", emptyList<String>(), offenders)
    }

    @Test
    fun noCodeNamesThePlatformWithoutAnImport() {
        val offenders = checkedFiles.flatMap { file ->
            file.codeLines().filter { (_, line) -> !line.trim().startsWith("import ") }
                .filter { (_, line) -> PLATFORM_USE.containsMatchIn(line) }
                .map { (number, line) -> "${file.relative()}:$number ${line.trim()}" }
        }
        assertEquals("the logic may not use the JVM or Android directly", emptyList<String>(), offenders)
    }

    @Test
    fun noLogicFileReferencesATypeOnlyAnAdapterDeclares() {
        val adapterNames = ADAPTER_FILES.keys.flatMap { declaredNames(File(sourceRoot, it), typesOnly = true) }.toSet()
        val logicNames = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.relative() !in ADAPTER_FILES }
            .flatMap { declaredNames(it, typesOnly = true) }.toSet()
        val adapterOnly = adapterNames - logicNames
        assertTrue("the adapters declare nothing?", adapterOnly.size > 10)
        // Crypto is the facade over the JCA seam that was isolated before this check existed.
        val offenders = checkedFiles.filter { it.relative() != "core/crypto/Crypto.kt" }.flatMap { file ->
            file.codeLines().flatMap { (number, line) ->
                adapterOnly.filter { Regex("""\b$it\b""").containsMatchIn(line) }
                    .map { "${file.relative()}:$number names $it (declared by an adapter)" }
            }
        }
        assertEquals("the logic may only name the interfaces", emptyList<String>(), offenders)
    }

    @Test
    fun theAdapterListHoldsOnlyWhatIsStillNeeded() {
        ADAPTER_FILES.forEach { (path, reason) ->
            val file = File(sourceRoot, path)
            assertTrue("$path: write why it is an adapter", reason.length > 10)
            val needsIt = file.codeLines().any { (_, line) ->
                val import = IMPORT.matchEntire(line.trim())?.groupValues?.get(1)
                (import != null && forbidden(import)) || PLATFORM_USE.containsMatchIn(line)
            }
            assertTrue("$path no longer touches the platform: take it off the list", needsIt)
        }
    }

    @Test
    fun theCheckSeesWhatItShouldReject() {
        assertTrue(forbidden("android.content.Context"))
        assertTrue(forbidden("java.io.File"))
        assertTrue(forbidden("javax.crypto.Cipher"))
        assertTrue(forbidden("androidx.fragment.app.FragmentActivity"))
        assertTrue(forbidden("androidx.compose.ui.res.stringResource"))
        assertFalse(forbidden("androidx.room.Dao"))
        assertFalse(forbidden("androidx.lifecycle.ViewModel"))
        assertFalse(forbidden("kotlinx.io.files.Path"))
        assertTrue(PLATFORM_USE.containsMatchIn("val x = java.util.UUID.randomUUID()"))
        assertTrue(PLATFORM_USE.containsMatchIn("val t = System.currentTimeMillis()"))
        assertTrue(PLATFORM_USE.containsMatchIn("} catch (_: SecurityException) {"))
        assertTrue(PLATFORM_USE.containsMatchIn("Normalizer.normalize(a, Normalizer.Form.NFC)"))
        assertTrue(PLATFORM_USE.containsMatchIn("val c = Character.isISOControl(cp)"))
        assertFalse(PLATFORM_USE.containsMatchIn("val t = Clock.System.now()"))
        assertTrue(PLATFORM_USE.containsMatchIn("e.javaClass.simpleName"))
        assertFalse(PLATFORM_USE.containsMatchIn("val language = Locale.javascript"))
        assertEquals(
            listOf("Foo", "bar"),
            declaredNames("class Foo\nfun Foo.bar() = 1\n  class Nested\nprivate fun hid()"),
        )
    }

    private fun declaredNames(file: File, typesOnly: Boolean): List<String> = declaredNames(file.readText(), typesOnly)

    /**
     * Top-level (unindented, non-private) types, and functions unless [typesOnly], a source declares. The
     * seam functions of the Jvm* files are meant to be called by name; only the types are checked.
     */
    private fun declaredNames(text: String, typesOnly: Boolean = false): List<String> =
        text.lines().mapNotNull { line ->
            val groups = DECLARATION.find(line)?.groupValues ?: return@mapNotNull null
            (if (typesOnly) groups[1] else groups[1].ifEmpty { groups[2] }).ifEmpty { null }
        }

    private companion object {
        val IMPORT = Regex("""import ([\w.]+)(?: as \w+)?""")
        val STRING = Regex(""""(?:[^"\\]|\\.)*"""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val DECLARATION = Regex(
            """^(?:(?:internal|public|data|enum|sealed|abstract|open|fun)\s+)*(?:class|object|interface)\s+(\w+)""" +
                """|^(?:internal\s+)?fun\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)\(""",
        )

        // The packages, below `com.pasich.encly`, whose logic moves to common code.
        val LOGIC_PACKAGES = listOf(
            "domain",
            "data/repository",
            "data/backup",
            "data/importer",
            "data/export",
            "data/handoff",
            "data/zip",
            "data/model",
            "data/datasource",
            "core/backup",
            "core/crypto",
            "core/security",
            "core/common",
            "core/platform",
        )

        val FORBIDDEN_PREFIXES = listOf("android.", "androidx.", "java.", "javax.", "dagger.")

        // What common code may take from androidx: Room, sqlite, DataStore, lifecycle and annotations.
        val ALLOWED_ANDROIDX = listOf(
            "androidx.room.",
            "androidx.sqlite.",
            "androidx.datastore.",
            "androidx.lifecycle.",
            "androidx.annotation.",
        )

        // Direct uses of the platform that need no import: FQNs, System, javaClass, R, the JDK classes
        // that need no import (Character) or whose import the check would also catch (Normalizer).
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

        /**
         * The adapter files: the one place per platform service that may touch Android or the JVM,
         * with why. A Kotlin Multiplatform build turns each into an actual (or an Android source set).
         */
        val ADAPTER_FILES = mapOf(
            // Storage Access Framework: the document pickers' URIs and cursors.
            "data/backup/BackupDocuments.kt" to "SAF documents of the backup export and import",
            "data/importer/SafImportFiles.kt" to
                "SAF files and folders of the notes import; maps SecurityException to unreadable",
            "data/export/MarkdownDocuments.kt" to "SAF documents of the Markdown export",
            // Android IPC with My Notes.
            "data/handoff/HandoffRequest.kt" to "the hand-off Intent and its URI grant",
            "data/handoff/MyNotesCallerVerifier.kt" to "PackageManager caller verification of the hand-off",
            "data/handoff/CacheHandoffStagingSweeper.kt" to "the app cache directory from the Context",
            // java.* isolated by design.
            "data/zip/JvmZip.kt" to "java.util.zip: CRC-32, inflater, ZipFile, ZipOutputStream",
            "data/export/JvmUnicode.kt" to "java.text.Normalizer and Character categories for file names",
            "core/crypto/JcaCrypto.kt" to "javax.crypto and java.security: the JCA behind Crypto",
            "core/backup/JvmJsonBytes.kt" to "java.io byte-array streams so the plaintext payload can be wiped",
            "core/security/JvmVaultFileIo.kt" to "java.io and java.nio: the vault file's fsync and atomic replace",
            // The Android implementations of the platform interfaces.
            "core/platform/SharedPreferencesKeyValueStore.kt" to "SharedPreferences behind KeyValueStore",
            "core/platform/HandlerMainThreadTimer.kt" to "Handler and Looper behind MainThreadTimer",
            "core/common/AndroidAppStrings.kt" to "Android resources and AppCompat locales behind AppStrings",
            "core/common/AndroidUiText.kt" to "Android and Compose resource lookup of UiText",
            "core/security/SystemLockoutClock.kt" to "SystemClock and Settings.Global behind LockoutClock",
            "core/security/SystemDeviceLockWatcher.kt" to "KeyguardManager and the screen-off receiver",
            "core/security/KeystorePinFactor.kt" to "AndroidKeyStore HMAC key behind PinHardwareFactor",
            "core/security/KeystoreBiometricManager.kt" to "Keystore wrapping key and BiometricPrompt",
            "core/security/ActivityBiometricHost.kt" to "the FragmentActivity BiometricPrompt attaches to",
            "core/security/cipher/AndroidDatabaseState.kt" to "Context.getDatabasePath for the database header check",
        )
    }
}
