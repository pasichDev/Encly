package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A backup decrypted by onboarding's "Restore from backup", held in memory until first-run
 * setup has created the PIN and opened the new vault. It is imported before onboarding is
 * committed, so a process killed meanwhile leaves no committed (empty) vault: onboarding simply
 * starts over. It is never written anywhere else.
 */
@Singleton
class PendingRestore @Inject constructor(private val backupManager: BackupManager) {
    @Volatile
    private var payload: BackupPayload? = null

    fun stage(payload: BackupPayload) {
        this.payload = payload
    }

    fun clear() {
        payload = null
    }

    /**
     * Imports the staged backup into the vault setup just opened. Returns false only when a
     * staged restore failed; the payload then stays staged for a retry, and is dropped once
     * it was imported.
     */
    suspend fun apply(): Boolean {
        val staged = payload ?: return true
        return try {
            backupManager.import(staged, ImportMode.REPLACE)
            payload = null
            true
        } catch (_: BackupException) {
            false
        }
    }
}
