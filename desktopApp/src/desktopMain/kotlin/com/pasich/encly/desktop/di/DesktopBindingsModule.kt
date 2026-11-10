package com.pasich.encly.desktop.di

import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.backup.RoomVaultDataStore
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

/** The interface bindings `:app` declares in `DatabaseModule` and `SecurityBindingsModule`, for the desktop graph. */
@ContributesTo(AppScope::class)
@BindingContainer
interface DesktopBindingsModule {
    // SecureDatabaseManager is itself a singleton, so this binding is one instance too.
    @Binds
    fun bindDatabaseProvider(secureDatabaseManager: SecureDatabaseManager): DatabaseProvider

    @Binds
    fun bindVaultDataStore(store: RoomVaultDataStore): VaultDataStore

    @Binds
    fun bindVaultLockEvents(manager: SessionLockManager): VaultLockEvents

    @Binds
    fun bindAutoLockPolicy(autoLock: AutoLock): AutoLockPolicy
}
