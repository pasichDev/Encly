package com.pasich.encly.release

import com.pasich.encly.core.LINK_PRIVACY_POLICY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Guards what the stores read from the repository, so a release cannot fail in Play Console or
 * F-Droid because of a missing file or an over-long text:
 * - fastlane/metadata/android: one listing per shipped language, Play's length limits, release
 *   notes for the current versionCode;
 * - .github/workflows/publish-play.yml: can only be started by hand and only when enabled.
 */
class StoreMetadataTest {

    private val root = listOf(File(".."), File("."))
        .first { File(it, "version.properties").isFile }

    private val metadataDir = File(root, "fastlane/metadata/android")

    private val version = Properties().apply {
        File(root, "version.properties").inputStream().use { load(it) }
    }
    private val major = version.getProperty("VERSION_MAJOR").trim().toInt()
    private val minor = version.getProperty("VERSION_MINOR").trim().toInt()
    private val patch = version.getProperty("VERSION_PATCH").trim().toInt()
    private val versionCode = major * CODE_MAJOR + minor * CODE_MINOR + patch

    @Test
    fun everyStoreLocaleHasAListing() {
        val present = metadataDir.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        assertEquals(STORE_LOCALES, present)
    }

    @Test
    fun storeLocalesCoverExactlyTheShippedAppLanguages() {
        val resDir = File(root, "app/src/main/res")
        val appLanguages = resDir.listFiles().orEmpty()
            .filter { it.isDirectory && APP_LOCALE_DIR.matches(it.name) }
            .map { it.name.removePrefix("values-").substringBefore('-') }
            .toSet() + DEFAULT_LANGUAGE
        assertEquals(appLanguages, STORE_LOCALES.map { it.substringBefore('-') }.toSet())
    }

    @Test
    fun textsFitPlayLimits() {
        STORE_LOCALES.forEach { locale ->
            LIMITS.forEach { (name, limit) ->
                val text = read(locale, name)
                val length = text.codePointCount(0, text.length)
                assertTrue("$locale/$name is empty", text.isNotBlank())
                assertTrue("$locale/$name has $length characters, limit $limit", length <= limit)
            }
        }
    }

    @Test
    fun titleNamesTheApp() {
        STORE_LOCALES.forEach { locale ->
            assertTrue(locale, read(locale, TITLE).startsWith(APP_NAME))
        }
    }

    @Test
    fun fullDescriptionLinksThePrivacyPolicyTheAppOpens() {
        STORE_LOCALES.forEach { locale ->
            assertTrue(locale, read(locale, FULL_DESCRIPTION).contains(LINK_PRIVACY_POLICY))
        }
    }

    @Test
    fun releaseNotesExistForTheCurrentVersionCode() {
        STORE_LOCALES.forEach { locale ->
            val notes = read(locale, "changelogs/$versionCode.txt")
            val length = notes.codePointCount(0, notes.length)
            assertTrue("$locale changelog for $versionCode is empty", notes.isNotBlank())
            assertTrue("$locale changelog has $length characters, limit $CHANGELOG_LIMIT", length <= CHANGELOG_LIMIT)
        }
    }

    @Test
    fun playPublishingRunsOnlyByHandAndOnlyWhenEnabled() {
        val lines = File(root, PUBLISH_WORKFLOW).readLines()
            .map { it.substringBefore(" #").trimEnd() }
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

        val triggers = section(lines, "on:").filter { it.indentation() == 2 }.map { it.trim() }
        assertEquals(listOf("workflow_dispatch:"), triggers)

        val jobs = section(lines, "jobs:")
        val jobNames = jobs.filter { it.indentation() == 2 }
        assertFalse("no jobs found", jobNames.isEmpty())
        jobNames.forEach { job ->
            val body = jobs.dropWhile { it != job }.drop(1).takeWhile { it.indentation() > 2 }
            val guard = body.filter { it.indentation() == 4 }.firstOrNull { it.trim().startsWith("if:") }
            assertEquals("${job.trim()} must be guarded", "if: $PUBLISH_GUARD", guard?.trim())
        }
    }

    private fun section(lines: List<String>, header: String): List<String> = lines.dropWhile {
        it != header
    }.drop(1).takeWhile {
        it.indentation() >
            0
    }

    private fun String.indentation(): Int = length - trimStart().length

    private fun read(locale: String, name: String): String {
        val file = File(metadataDir, "$locale/$name")
        assertTrue("missing $file", file.isFile)
        return file.readText().trim()
    }

    private companion object {
        const val APP_NAME = "Encly"
        const val DEFAULT_LANGUAGE = "en"
        const val CODE_MAJOR = 10000
        const val CODE_MINOR = 100
        const val TITLE = "title.txt"
        const val FULL_DESCRIPTION = "full_description.txt"
        const val CHANGELOG_LIMIT = 500
        const val PUBLISH_WORKFLOW = ".github/workflows/publish-play.yml"
        const val PUBLISH_GUARD = "vars.PLAY_PUBLISH_ENABLED == 'true'"

        /** Google Play locale codes; F-Droid accepts the same folder names. */
        val STORE_LOCALES = setOf("en-US", "uk", "de-DE", "fr-FR", "es-ES", "it-IT", "pl-PL", "pt-PT", "nl-NL")

        /** Play Console limits, in characters. */
        val LIMITS = mapOf(TITLE to 30, "short_description.txt" to 80, FULL_DESCRIPTION to 4000)

        val APP_LOCALE_DIR = Regex("""values-[a-z]{2,3}(-r[A-Z]{2})?""")
    }
}
