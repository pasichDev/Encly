package com.pasich.encly.core.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves text that is stored rather than shown (for example a duplicated note's title), in
 * the in-app language. The application context alone ignores that language on Android 12 and
 * lower, so the per-app locale is applied explicitly. Text that is only shown is [UiText].
 */
@Singleton
class AppStrings @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun get(@StringRes id: Int, vararg args: Any): String = localizedContext().getString(id, *args)

    private fun localizedContext(): Context {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(appLocales.toLanguageTags()))
        }
        return context.createConfigurationContext(configuration)
    }
}
