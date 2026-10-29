package com.pasich.encly.core.locale

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.language_name_de
import com.pasich.encly.core.resources.language_name_en
import com.pasich.encly.core.resources.language_name_es
import com.pasich.encly.core.resources.language_name_fr
import com.pasich.encly.core.resources.language_name_it
import com.pasich.encly.core.resources.language_name_nl
import com.pasich.encly.core.resources.language_name_pl
import com.pasich.encly.core.resources.language_name_pt
import com.pasich.encly.core.resources.language_name_uk
import com.pasich.encly.core.resources.language_system_default
import org.jetbrains.compose.resources.StringResource

/**
 * Languages the UI is translated into. [tag] matches the `values-<tag>` resource folder and
 * res/xml/locales_config.xml; [nativeName] is the language's own name, which is what the
 * picker shows so a user can find their language whatever the current UI language is.
 *
 * [SYSTEM] clears the per-app override and follows the device language.
 */
enum class AppLanguage(val tag: String?, val nativeName: StringResource) {
    SYSTEM(null, Res.string.language_system_default),
    ENGLISH("en", Res.string.language_name_en),
    UKRAINIAN("uk", Res.string.language_name_uk),
    GERMAN("de", Res.string.language_name_de),
    FRENCH("fr", Res.string.language_name_fr),
    SPANISH("es", Res.string.language_name_es),
    ITALIAN("it", Res.string.language_name_it),
    POLISH("pl", Res.string.language_name_pl),
    PORTUGUESE("pt", Res.string.language_name_pt),
    DUTCH("nl", Res.string.language_name_nl),
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
