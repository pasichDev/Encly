package com.pasich.encly.core.resources

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Base64
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The translations are kept in Android's format and converted for Compose resources (see
 * `ConvertAndroidStrings` in build-logic), so what the app shows must be what Android would have
 * shown. [AndroidStrings] reads the files the way Android reads them and formats the way
 * `Resources.getString` / `getQuantityString` format; every string and plural of every language
 * must come out of the Compose resources identical, in the language selected by the default
 * locale. A second check reads the shipped data, which also reaches the plural categories no whole
 * number selects, and allows one difference from the Android text: the format arguments Compose
 * resources cannot fill in as written (`%d` becomes `%1$d`, `%%` becomes `%`).
 */
class AndroidStringsConversionTest {
    private val savedLocale = Locale.getDefault()
    private val snapshots = AndroidStrings.languages.associate { it.tag to AndroidStrings.read(it) }
    private val english = snapshots.getValue("en")

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun theSameKeysExist() {
        assertEquals(english.strings.keys, Res.allStringResources.keys)
        assertEquals(english.plurals.keys, Res.allPluralStringResources.keys)
        assertTrue(english.strings.size > MIN_STRINGS, "the snapshot is empty")
        AndroidStrings.languages.forEach { language ->
            val own = snapshots.getValue(language.tag)
            assertTrue(own.strings.keys.all { it in english.strings }, "${language.tag} has keys English lacks")
            assertEquals(english.plurals.keys, own.plurals.keys, "${language.tag} plurals")
        }
    }

    @Test
    fun everyStringReadsTheSameInEveryLanguage() {
        val different = mutableListOf<String>()
        var compared = 0
        AndroidStrings.languages.forEach { language ->
            val own = snapshots.getValue(language.tag)
            english.strings.keys.forEach { key ->
                // Strings that are not translated (brand names, language names) have English text only.
                val old = own.strings[key] ?: english.strings.getValue(key)
                val args = AndroidStrings.sampleArguments(old)
                val expected = if (AndroidStrings.hasSpecifiers(
                        old,
                    )
                ) {
                    AndroidStrings.format(old, args)
                } else {
                    old
                }
                val actual =
                    inLocale(language.tag) { runBlocking { getString(Res.allStringResources.getValue(key), *args) } }
                compared++
                if (expected != actual) different += "${language.tag}/$key: '$expected' != '$actual'"
            }
        }
        assertTrue(
            different.isEmpty(),
            "${different.size} of $compared differ:\n" + different.take(MAX_REPORTED).joinToString("\n"),
        )
    }

    @Test
    fun everyPluralReadsTheSameInEveryLanguage() {
        val different = mutableListOf<String>()
        var compared = 0
        AndroidStrings.languages.forEach { language ->
            val own = snapshots.getValue(language.tag)
            val reachable = QUANTITIES.getValue(language.tag)
            english.plurals.keys.forEach { key ->
                val plural = own.plurals.getValue(key)
                reachable.forEach { (category, quantity) ->
                    val old = plural.items.getValue(category)
                    val args = AndroidStrings.sampleArguments(old)
                    val expected = if (AndroidStrings.hasSpecifiers(
                            old,
                        )
                    ) {
                        AndroidStrings.format(old, args)
                    } else {
                        old
                    }
                    val actual = inLocale(language.tag) {
                        runBlocking { getPluralString(Res.allPluralStringResources.getValue(key), quantity, *args) }
                    }
                    compared++
                    if (expected !=
                        actual
                    ) {
                        different += "${language.tag}/$key[$category=$quantity]: '$expected' != '$actual'"
                    }
                }
            }
        }
        assertTrue(
            different.isEmpty(),
            "${different.size} of $compared differ:\n" + different.take(MAX_REPORTED).joinToString("\n"),
        )
    }

    @Test
    fun theShippedTextIsTheAndroidTextWithOnlyTheFormatRewrite() {
        val different = mutableListOf<String>()
        var rewritten = 0
        AndroidStrings.languages.forEach { language ->
            val own = snapshots.getValue(language.tag)
            val shipped = shipped(language.folder)
            assertEquals(own.strings.keys, shipped.strings.keys, "${language.tag} strings")
            assertEquals(own.plurals.keys, shipped.plurals.keys, "${language.tag} plurals")
            own.strings.forEach { (key, old) ->
                val new = shipped.strings.getValue(key)
                if (rewrite(old) != new) different += "${language.tag}/$key: '${rewrite(old)}' != '$new'"
                if (rewrite(old) != old) rewritten++
            }
            own.plurals.forEach { (key, plural) ->
                assertEquals(
                    plural.items.keys,
                    shipped.plurals.getValue(key).items.keys,
                    "${language.tag}/$key quantities",
                )
                plural.items.forEach { (quantity, old) ->
                    val new = shipped.plurals.getValue(key).items.getValue(quantity)
                    if (rewrite(old) != new) different += "${language.tag}/$key[$quantity]: '${rewrite(old)}' != '$new'"
                    if (rewrite(old) != old) rewritten++
                }
            }
        }
        assertTrue(different.isEmpty(), different.take(MAX_REPORTED).joinToString("\n"))
        assertTrue(rewritten > 0, "no format argument was rewritten?")
    }

    private fun <T> inLocale(tag: String, block: () -> T): T {
        Locale.setDefault(Locale.forLanguageTag(tag))
        try {
            return block()
        } finally {
            Locale.setDefault(savedLocale)
        }
    }

    /** `%d` becomes `%<n>$d`, numbered in order of appearance, and `%%` becomes `%`. */
    private fun rewrite(text: String): String {
        var counter = 0
        return text.replace(BARE_INT) { "%${++counter}\$d" }.replace("%%", "%")
    }

    private class Shipped(val strings: Map<String, String>, val plurals: Map<String, AndroidStrings.Plural>)

    /** The data the build made of the XML (`*.cvr`), decoded: what the resource reader is handed. */
    private fun shipped(folder: String): Shipped {
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, AndroidStrings.Plural>()
        listOf("strings", "strings_handoff", "strings_import", "strings_security").forEach { file ->
            val bytes = runBlocking { Res.readBytes("$folder/$file.commonMain.cvr") }
            bytes.decodeToString().lineSequence().filter { it.isNotBlank() && it != "version:0" }.forEach { line ->
                val (type, key, data) = line.split('|')
                when (type) {
                    "string" -> strings[key] = unBase64(data)

                    "plurals" -> plurals[key] = AndroidStrings.Plural(
                        data.split(',').associate {
                            it.substringBefore(':').lowercase() to
                                unBase64(it.substringAfter(':'))
                        },
                    )

                    else -> error("unexpected record $type")
                }
            }
        }
        return Shipped(strings, plurals)
    }

    private fun unBase64(data: String) = String(Base64.getDecoder().decode(data), Charsets.UTF_8)

    private companion object {
        const val MIN_STRINGS = 700
        const val MAX_REPORTED = 20

        val BARE_INT = Regex("""(?<!%)%d""")

        /**
         * A quantity for each plural category an integer can have in a language (CLDR): the
         * category `other` of Ukrainian and Polish is for fractions only, so no whole number
         * reaches it; its text is checked in the shipped-data test.
         */
        val QUANTITIES = mapOf(
            "en" to mapOf("one" to 1, "other" to 2),
            "de" to mapOf("one" to 1, "other" to 2),
            "nl" to mapOf("one" to 1, "other" to 2),
            "fr" to mapOf("one" to 1, "many" to 1_000_000, "other" to 2),
            "es" to mapOf("one" to 1, "many" to 1_000_000, "other" to 2),
            "it" to mapOf("one" to 1, "many" to 1_000_000, "other" to 2),
            "pt" to mapOf("one" to 1, "many" to 1_000_000, "other" to 2),
            "uk" to mapOf("one" to 1, "few" to 2, "many" to 5),
            "pl" to mapOf("one" to 1, "few" to 2, "many" to 5),
        )
    }
}
