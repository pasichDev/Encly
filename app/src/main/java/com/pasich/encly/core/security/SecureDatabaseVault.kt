package com.pasich.encly.core.security

import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.database.VaultFile
import dev.zacsweers.metro.Inject

/**
 * [VaultDatabase] on the app's [SecureDatabaseManager]: the security core opens, closes and deletes the
 * encrypted database through this port and never sees Room or the cipher.
 */
@Inject
class SecureDatabaseVault(private val manager: SecureDatabaseManager) : VaultDatabase {
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
