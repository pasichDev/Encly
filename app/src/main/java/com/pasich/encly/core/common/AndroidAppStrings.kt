package com.pasich.encly.core.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [AppStrings] on the Android resources. The application context alone ignores the in-app
 * language on Android 12 and lower, so the per-app locale is applied explicitly.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidAppStrings(@param:ApplicationContext private val context: Context) : AppStrings {

    override fun get(@StringRes id: Int, vararg args: Any): String = localizedContext().getString(id, *args)

    private fun localizedContext(): Context {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(appLocales.toLanguageTags()))
        }
        return context.createConfigurationContext(configuration)
    }
}
