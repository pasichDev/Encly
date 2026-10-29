package com.pasich.encly.core.di

import com.pasich.encly.data.backup.RoomVaultDataStore
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
@BindingContainer
interface DatabaseModule {
    // SecureDatabaseManager is itself a singleton, so this binding is one instance too.
    @Binds
    fun bindDatabaseProvider(secureDatabaseManager: SecureDatabaseManager): DatabaseProvider

    @Binds
    fun bindVaultDataStore(store: RoomVaultDataStore): VaultDataStore
}
