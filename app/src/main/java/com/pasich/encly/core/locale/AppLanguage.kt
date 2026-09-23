package com.pasich.encly.core.locale

import androidx.annotation.StringRes
import com.pasich.encly.R

/**
 * Languages the UI is translated into. [tag] matches the `values-<tag>` resource folder and
 * res/xml/locales_config.xml; [nativeName] is the language's own name, which is what the
 * picker shows so a user can find their language whatever the current UI language is.
 *
 * [SYSTEM] clears the per-app override and follows the device language.
 */
enum class AppLanguage(val tag: String?, @param:StringRes val nativeName: Int) {
    SYSTEM(null, R.string.language_system_default),
    ENGLISH("en", R.string.language_name_en),
    UKRAINIAN("uk", R.string.language_name_uk),
    GERMAN("de", R.string.language_name_de),
    FRENCH("fr", R.string.language_name_fr),
    SPANISH("es", R.string.language_name_es),
    ITALIAN("it", R.string.language_name_it),
    POLISH("pl", R.string.language_name_pl),
    PORTUGUESE("pt", R.string.language_name_pt),
    DUTCH("nl", R.string.language_name_nl),
    ;

    companion object {
        /**
         * Maps a BCP 47 tag (e.g. "de", "pt-BR", "uk-UA") to a supported language by its
         * primary subtag. A null/blank or unsupported tag means no supported override: SYSTEM.
         */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            if (language.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
