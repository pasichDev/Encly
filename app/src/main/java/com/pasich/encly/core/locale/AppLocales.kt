package com.pasich.encly.core.locale

import android.content.res.Resources
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

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
        useAsDefaultLocale(language)
    }

    /**
     * Makes the process's default locale the stored in-app language. Every activity calls it in
     * onCreate, before it composes anything: after a relaunch nothing else has applied the stored
     * language to the process yet, and the strings read it from there.
     */
    fun syncDefaultLocale() = useAsDefaultLocale(current())

    /**
     * The strings are Compose resources, which pick their translation from the process's default
     * locale (java.util.Locale and android.os.LocaleList), not from an activity's configuration.
     * AppCompat sets it when it recreates an activity; setting it here as well makes text resolved
     * outside an activity (stored copies, ViewModel messages) follow the language at once.
     * [AppLanguage.SYSTEM] goes back to the device's locales.
     */
    private fun useAsDefaultLocale(language: AppLanguage) {
        val locales = language.tag?.let { LocaleList.forLanguageTags(it) }
            ?: Resources.getSystem().configuration.locales
        LocaleList.setDefault(locales)
        Locale.setDefault(locales[0])
    }

    fun localeListFor(language: AppLanguage): LocaleListCompat =
        language.tag?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
}
