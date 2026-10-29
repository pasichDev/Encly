package com.pasich.encly.core.di

import android.content.Context
import com.pasich.encly.data.backup.RoomVaultDataStore
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.database.AndroidDatabasePlatform
import com.pasich.encly.data.database.DatabasePlatform
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
interface DatabaseModule {
    // SecureDatabaseManager is itself a singleton, so this binding is one instance too.
    @Binds
    fun bindDatabaseProvider(secureDatabaseManager: SecureDatabaseManager): DatabaseProvider

    @Binds
    fun bindVaultDataStore(store: RoomVaultDataStore): VaultDataStore
}

@ContributesTo(AppScope::class)
@BindingContainer
object DatabasePlatformModule {
    /** Where the vault's files are: the app's own database directory, as ever. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabasePlatform(@ApplicationContext context: Context): DatabasePlatform =
        AndroidDatabasePlatform(context)
}
