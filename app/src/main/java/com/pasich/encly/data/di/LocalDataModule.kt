package com.pasich.encly.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

private val Context.dataStore by preferencesDataStore("my_notes_settings")

@ContributesTo(AppScope::class)
@BindingContainer
object LocalDataModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}
