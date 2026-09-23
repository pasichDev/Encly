package com.pasich.encly.i18n

import com.pasich.encly.core.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the translations without an emulator: every locale must carry every translatable key
 * of the default (English) resources, with the same format arguments, the plural quantities
 * its language needs, and be listed in res/xml/locales_config.xml and [AppLanguage].
 *
 * Lint's MissingTranslation (an error in this build) checks the first rule too; this test
 * also covers format arguments, plural categories, unescaped apostrophes and the locale lists.
 */
class TranslationCompletenessTest {

    private val resDir = listOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }

    /** Folders such as values-uk or values-pt-rBR; not values-night, values-v31, ... */
    private val localeDirs: Map<String, File> = resDir.listFiles().orEmpty()
        .filter { it.isDirectory && LOCALE_DIR.matches(it.name) }
        .associateBy { it.name.removePrefix("values-") }

    private val defaults = parse(File(resDir, "values"))

    @Test
    fun defaultResourcesAreNotEmpty() {
        assertTrue("no default strings found in $resDir", defaults.strings.size > MIN_EXPECTED_KEYS)
    }

    @Test
    fun everyLocaleHasEveryKeyAndNoExtraKeys() {
        assertTrue("no translations found", localeDirs.isNotEmpty())
        localeDirs.forEach { (locale, dir) ->
            val translated = parse(dir)
            val missing = (defaults.strings.keys - translated.strings.keys) +
                (defaults.plurals.keys - translated.plurals.keys) +
                (defaults.arrays.keys - translated.arrays.keys)
            val extra = (translated.strings.keys - defaults.strings.keys) +
                (translated.plurals.keys - defaults.plurals.keys) +
                (translated.arrays.keys - defaults.arrays.keys)
            assertTrue("values-$locale is missing: ${missing.sorted()}", missing.isEmpty())
            assertTrue("values-$locale has keys unknown to values/: ${extra.sorted()}", extra.isEmpty())
        }
    }

    @Test
    fun formatArgumentsMatchTheDefaultLocale() {
        localeDirs.forEach { (locale, dir) ->
            val translated = parse(dir)
            defaults.strings.forEach { (key, text) ->
                val value = translated.strings[key] ?: return@forEach
                assertEquals("values-$locale/$key", formatArgs(text), formatArgs(value))
            }
            defaults.plurals.forEach { (key, items) ->
                val expected = formatArgs(items.getValue("other"))
                translated.plurals[key]?.forEach { (quantity, value) ->
                    assertEquals("values-$locale/$key[$quantity]", expected, formatArgs(value))
                }
            }
        }
    }

    @Test
    fun pluralsDefineTheCategoriesOfTheirLanguage() {
        val all = localeDirs.mapValues { parse(it.value) } + ("en" to defaults)
        all.forEach { (locale, resources) ->
            val language = locale.substringBefore('-')
            val required = REQUIRED_QUANTITIES[language]
                ?: error("Add the CLDR plural categories of '$language' to REQUIRED_QUANTITIES")
            resources.plurals.forEach { (key, items) ->
                val missing = required - items.keys
                assertTrue("$locale/$key lacks quantities $missing", missing.isEmpty())
            }
        }
    }

    @Test
    fun apostrophesAndQuotesAreEscaped() {
        (localeDirs.values + File(resDir, "values")).forEach { dir ->
            val parsed = parse(dir)
            val texts = parsed.strings.mapValues { listOf(it.value) } +
                parsed.plurals.mapValues { it.value.values.toList() }
            texts.forEach { (key, values) ->
                values.forEach { raw ->
                    assertTrue(
                        "${dir.name}/$key has an unescaped ' or \": $raw",
                        !UNESCAPED_QUOTE.containsMatchIn(raw),
                    )
                }
            }
        }
    }

    @Test
    fun localeConfigListsExactlyTheShippedLocales() {
        val config = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "xml/locales_config.xml"))
        val nodes = config.getElementsByTagName("locale")
        val declared = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttribute("android:name") }
            .toSet()
        val shipped = localeDirs.keys.map { it.replace("-r", "-") }.toSet() + DEFAULT_LOCALE
        assertEquals(shipped, declared)
    }

    @Test
    fun languagePickerOffersExactlyTheShippedLocales() {
        val offered = AppLanguage.entries.mapNotNull { it.tag }.toSet()
        val shipped = localeDirs.keys.map { it.substringBefore('-') }.toSet() + DEFAULT_LOCALE
        assertEquals(shipped, offered)
    }

    private class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
    )

    /** Raw text of every translatable string, plural and literal string-array in a values dir. */
    private fun parse(dir: File): Resources {
        val elements = dir.listFiles { file -> file.extension == "xml" }.orEmpty()
            .flatMap { readElements(it) }
            .filter { it.getAttribute("translatable") != "false" }
        return Resources(
            strings = elements.filter { it.tagName == "string" }
                .associate { it.getAttribute("name") to it.textContent },
            plurals = elements.filter { it.tagName == "plurals" }
                .associate { plural ->
                    plural.getAttribute("name") to plural.children()
                        .associate { it.getAttribute("quantity") to it.textContent }
                },
            arrays = elements.filter { it.tagName == "string-array" }
                .mapNotNull { array -> literalItems(array)?.let { array.getAttribute("name") to it } }
                .toMap(),
        )
    }

    private fun readElements(file: File): List<Element> =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.children()

    /** An array of @string references is localized through those strings, so it is skipped. */
    private fun literalItems(array: Element): List<String>? = array.children().map { it.textContent.trim() }
        .takeUnless { items -> items.all { it.startsWith("@string/") } }

    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull {
        childNodes.item(it) as? Element
    }

    private fun formatArgs(text: String): List<String> = FORMAT_ARG.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        const val DEFAULT_LOCALE = "en"
        const val MIN_EXPECTED_KEYS = 100
        val LOCALE_DIR = Regex("""values-[a-z]{2,3}(-r[A-Z]{2})?""")
        val FORMAT_ARG = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

        /** A ' or " not preceded by a backslash: aapt drops it or fails the build. */
        val UNESCAPED_QUOTE = Regex("""(?<!\\)['"]""")

        /** CLDR plural categories that Android selects for each shipped language. */
        val REQUIRED_QUANTITIES = mapOf(
            "en" to setOf("one", "other"),
            "de" to setOf("one", "other"),
            "nl" to setOf("one", "other"),
            "fr" to setOf("one", "many", "other"),
            "es" to setOf("one", "many", "other"),
            "it" to setOf("one", "many", "other"),
            "pt" to setOf("one", "many", "other"),
            "uk" to setOf("one", "few", "many", "other"),
            "pl" to setOf("one", "few", "many", "other"),
        )
    }
}
