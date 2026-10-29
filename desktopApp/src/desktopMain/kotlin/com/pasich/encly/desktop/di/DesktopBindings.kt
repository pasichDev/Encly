package com.pasich.encly.desktop.di

import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultDatabase
import com.pasich.encly.core.security.VaultKind
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.backup.RoomVaultDataStore
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.database.VaultFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject

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
    fun bindVaultDatabase(database: DesktopSecureDatabaseVault): VaultDatabase

    @Binds
    fun bindVaultLockEvents(manager: SessionLockManager): VaultLockEvents

    @Binds
    fun bindAutoLockPolicy(autoLock: AutoLock): AutoLockPolicy
}

/**
 * [VaultDatabase] on [SecureDatabaseManager]: the security core opens, closes and deletes the encrypted
 * database through this port and never sees Room or the cipher. The same adapter as `:app`'s.
 */
@Inject
class DesktopSecureDatabaseVault(private val manager: SecureDatabaseManager) : VaultDatabase {
    override fun isDatabaseUnlocked(): Boolean = manager.isDatabaseUnlocked()

    override fun hasEncryptedDatabase(vault: VaultKind): Boolean = manager.hasEncryptedDatabase(vault.file())

    override fun unlockDatabase(dek: ByteArray, allowCreate: Boolean): Boolean =
        manager.unlockDatabase(dek, allowCreate)

    override fun unlockDatabase(dek: ByteArray, allowCreate: Boolean, vault: VaultKind): Boolean =
        manager.unlockDatabase(dek, allowCreate, vault.file())

    override fun reset() = manager.reset()

    override fun wipe() = manager.wipe()

    override fun wipeIfClosed(only: VaultKind?): Boolean = manager.wipeIfClosed(only?.file())

    private fun VaultKind.file(): VaultFile = when (this) {
        VaultKind.PRIMARY -> VaultFile.PRIMARY
        VaultKind.DECOY -> VaultFile.DECOY
    }
}
