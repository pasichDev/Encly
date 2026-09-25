package com.pasich.encly.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language, backed by AppCompatDelegate.setApplicationLocales.
 *
 * On Android 13+ the framework stores the choice (and it also shows up in the system's
 * per-app language settings, driven by res/xml/locales_config.xml). On older versions
 * AppCompat stores it itself (AppLocalesMetadataHolderService with autoStoreLocales in the
 * manifest) and applies it to every AppCompatActivity. Either way the change recreates the
 * running activity, so the UI re-reads its strings in the new language.
 */
object AppLocales {

    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }

    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(localeListFor(language))
    }

    fun localeListFor(language: AppLanguage): LocaleListCompat =
        language.tag?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
}
