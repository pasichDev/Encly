package com.pasich.encly.core.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Resolves text that is stored rather than shown (for example a duplicated note's title), in
 * the in-app language. The application context alone ignores that language on Android 12 and
 * lower, so the per-app locale is applied explicitly. Text that is only shown is [UiText].
 */
@SingleIn(AppScope::class)
@Inject
class AppStrings(@param:ApplicationContext private val context: Context) {

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
