package com.pasich.encly.whatsnew

import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.platform.HostPlatform

/**
 * The notes of one release: [heading] is the first line of the store changelog ("Encly 2.3.1"), [body] the
 * changes under it.
 */
data class ReleaseNotes(val heading: String, val body: String)

/**
 * The store changelogs the app was built with ([BundledChangelogs]), by language. The files are
 * `fastlane/metadata/<platform>/<folder>/changelogs/<versionCode>.txt`; [folderFor] maps the app's languages to the
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
        AppLanguage.ARABIC -> "ar"
        AppLanguage.ENGLISH, AppLanguage.SYSTEM -> FALLBACK
    }

    /**
     * The notes of this build in [languageTag], else in English, else null (no changelog was written for it). The
     * iOS and desktop apps have notes of their own ([bundledFor]); the Play ones never reach them.
     */
    fun current(languageTag: String?, platform: HostPlatform = HostPlatform.ANDROID): ReleaseNotes? =
        notes(languageTag, bundledFor(platform))

    /** The notes (locale folder to text) the build carries for [platform]; empty when it has none. */
    fun bundledFor(platform: HostPlatform): Map<String, String> = when (platform) {
        HostPlatform.ANDROID -> BundledChangelogs.notes
        HostPlatform.IOS -> BundledChangelogs.hostNotes["ios"].orEmpty()
        HostPlatform.DESKTOP -> BundledChangelogs.hostNotes["desktop"].orEmpty()
    }

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
