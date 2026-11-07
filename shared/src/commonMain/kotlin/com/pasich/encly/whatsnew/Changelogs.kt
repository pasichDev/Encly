package com.pasich.encly.whatsnew

import com.pasich.encly.core.locale.AppLanguage

/**
 * The notes of one release: [heading] is the first line of the store changelog ("Encly 2.3.1"), [body] the
 * changes under it.
 */
data class ReleaseNotes(val heading: String, val body: String)

/**
 * The store changelogs the app was built with ([BundledChangelogs]), by language. The files are
 * `fastlane/metadata/android/<folder>/changelogs/<versionCode>.txt`; [folderFor] maps the app's languages to the
 * folders, and a language without notes reads the English ones.
 */
object Changelogs {
    /** The folder of English, the notes every other language falls back to. */
    const val FALLBACK = "en-US"

    /** The store folder for the BCP 47 [languageTag] of the app's language (null or an unsupported one: English). */
    fun folderFor(languageTag: String?): String = when (AppLanguage.fromTag(languageTag)) {
        AppLanguage.UKRAINIAN -> "uk"
        AppLanguage.GERMAN -> "de-DE"
        AppLanguage.FRENCH -> "fr-FR"
        AppLanguage.SPANISH -> "es-ES"
        AppLanguage.ITALIAN -> "it-IT"
        AppLanguage.POLISH -> "pl-PL"
        AppLanguage.PORTUGUESE -> "pt-PT"
        AppLanguage.DUTCH -> "nl-NL"
        AppLanguage.ENGLISH, AppLanguage.SYSTEM -> FALLBACK
    }

    /** The notes of this build in [languageTag], else in English, else null (no changelog was written for it). */
    fun current(languageTag: String?): ReleaseNotes? = notes(languageTag, BundledChangelogs.notes)

    /** The notes in [languageTag] out of [bundled] (locale folder to text), else the English ones, else null. */
    fun notes(languageTag: String?, bundled: Map<String, String>): ReleaseNotes? {
        val text = bundled[folderFor(languageTag)] ?: bundled[FALLBACK] ?: return null
        return parse(text)
    }

    private fun parse(text: String): ReleaseNotes? {
        val lines = text.trim().lines()
        val heading = lines.firstOrNull()?.trim().orEmpty()
        if (heading.isEmpty()) return null
        return ReleaseNotes(heading, lines.drop(1).joinToString("\n").trim())
    }
}
