package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guard behind the backup allow-list: every key the app reads or writes in its flag store
 * (`KeyValueStore`) or its DataStore must be classified in [BackupSettings], either as a
 * preference that is backed up ([BackupSettings.included]) or as one that is not, with a reason
 * ([BackupSettings.excluded]). Adding a preference without deciding fails here, so a new
 * setting is never silently left out of a backup, and a security flag is never silently put in.
 *
 * It reads the production sources: a key is the first argument of a `getString`, `getBoolean`,
 * `getLong`, `putString`, `putBoolean`, `putLong` or `...PreferencesKey` call in a file that
 * uses `KeyValueStore` or a DataStore, resolved through `const val` names (keys are UPPER_CASE
 * constants; a literal works too). The vault state
 * file (PIN, lockout, recovery slots) is a different store and is not scanned; it is never
 * backed up.
 */
class PreferenceKeysClassifiedTest {

    @Test
    fun everyPreferenceKeyTheAppUsesIsClassified() {
        val unclassified = usedKeys().filterKeys { it !in BackupSettings.included && it !in BackupSettings.excluded }

        assertTrue(
            "Classify these preference keys in BackupSettings (included = backed up, excluded = not, with a " +
                "reason): ${unclassified.map { (key, where) -> "$key (${where.joinToString()})" }}",
            unclassified.isEmpty(),
        )
    }

    @Test
    fun noClassifiedKeyIsStale() {
        val used = usedKeys().keys
        val stale = (BackupSettings.included.keys + BackupSettings.excluded.keys).filter { it !in used }

        assertEquals("Keys in BackupSettings that no source uses any more", emptyList<String>(), stale)
    }

    @Test
    fun theScanSeesTheKeysItIsMeantToGuard() {
        val used = usedKeys().keys

        // If the scan silently found nothing, the two tests above would pass for the wrong reason.
        assertTrue(used.containsAll(listOf("theme_type", "auto_lock_delay", "onboarding_shown_v3", "app_language")))
    }

    /** Key -> the files that use it. */
    private fun usedKeys(): Map<String, Set<String>> {
        val sources = productionSources()
        val consts = constants(sources)
        val keys = linkedMapOf<String, MutableSet<String>>()
        sources.filter { (_, text) -> USES_PREFERENCES.containsMatchIn(text) }
            .filterNot { (file, _) -> file.name in STORE_IMPLEMENTATIONS }
            .forEach { (file, text) ->
                CALL.findAll(text).forEach { match ->
                    val literal = match.groupValues[1]
                    val reference = match.groupValues[2]
                    // A lower-case name is a parameter (`key`) or a resource (`Res.string.x`), not a key.
                    if (literal.isEmpty() && !CONSTANT_NAME.matches(reference.substringAfterLast('.'))) return@forEach
                    val values = if (literal.isNotEmpty()) setOf(literal) else resolve(reference, consts)
                    check(values.isNotEmpty()) {
                        "${file.name}: cannot tell which preference key `${match.value.trim()}` is; " +
                            "use a named string constant"
                    }
                    values.forEach { keys.getOrPut(it) { linkedSetOf() }.add(file.name) }
                }
            }
        return keys
    }

    private fun resolve(reference: String, consts: Map<String, Set<String>>): Set<String> =
        consts[reference.substringAfterLast('.')].orEmpty()

    /** `const val NAME = "value"` and `const val NAME = Other.NAME`, followed to the literal. */
    private fun constants(sources: List<Pair<File, String>>): Map<String, Set<String>> {
        val literal = mutableMapOf<String, MutableSet<String>>()
        val alias = mutableMapOf<String, MutableSet<String>>()
        sources.forEach { (_, text) ->
            CONST.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                val string = match.groupValues[2]
                val other = match.groupValues[3]
                if (string.isNotEmpty()) {
                    literal.getOrPut(name) { linkedSetOf() }.add(string)
                } else {
                    alias.getOrPut(name) { linkedSetOf() }.add(other.substringAfterLast('.'))
                }
            }
        }
        repeat(ALIAS_DEPTH) {
            alias.forEach { (name, targets) ->
                targets.forEach { target ->
                    literal[target]?.let { literal.getOrPut(name) { linkedSetOf() }.addAll(it) }
                }
            }
        }
        return literal
    }

    private fun productionSources(): List<Pair<File, String>> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        return root.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") && dir.name !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && it.extension == "kt" && isProduction(it, root) }
            .map { it to it.readText() }
            .toList()
    }

    /** `<module>/src/<set>/...` where the set is `main` or ends in `Main` (commonMain, desktopMain, ...). */
    private fun isProduction(file: File, root: File): Boolean {
        val parts = file.relativeTo(root).invariantSeparatorsPath.split('/')
        val src = parts.indexOf("src")
        if (src < 0 || src + 1 >= parts.size) return false
        val set = parts[src + 1]
        return set == "main" || set.endsWith("Main")
    }

    private companion object {
        val SKIPPED_DIRECTORIES = setOf("build", "node_modules", "notes")

        /** The interface and its three platform implementations take keys as parameters, not as keys. */
        val STORE_IMPLEMENTATIONS = setOf(
            "KeyValueStore.kt",
            "SharedPreferencesKeyValueStore.kt",
            "FileKeyValueStore.kt",
            "IosKeyValueStore.kt",
        )

        val USES_PREFERENCES = Regex("""KeyValueStore|PreferencesKey\(""")

        /** A store call's first argument: a string literal (group 1) or a name (group 2). */
        val CALL = Regex(
            """\b(?:getString|getBoolean|getLong|putString|putBoolean|putLong|""" +
                """(?:boolean|int|string|long|double|float|stringSet)PreferencesKey)""" +
                """\(\s*(?:"([^"]+)"|([A-Za-z_][A-Za-z0-9_.]*))""",
        )

        val CONSTANT_NAME = Regex("[A-Z][A-Z0-9_]*")

        val CONST = Regex(
            """const\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String\s*)?=\s*""" +
                """(?:"([^"]*)"|([A-Za-z_][A-Za-z0-9_.]*))""",
        )

        const val ALIAS_DEPTH = 3
    }
}
