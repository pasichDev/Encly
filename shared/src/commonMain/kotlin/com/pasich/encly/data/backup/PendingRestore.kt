package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupPayload
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile

/**
 * A backup decrypted by onboarding's "Restore from backup", held in memory until first-run
 * setup has created the PIN and opened the new vault. It is imported before onboarding is
 * committed, and setup marks the vault restore-pending first (see
 * [com.pasich.encly.core.security.SecurityManager.markRestorePending]), so a process killed
 * meanwhile leaves no committed (empty or partial) vault: the next start deletes it and
 * onboarding starts over. The payload itself is never written anywhere.
 */
@SingleIn(AppScope::class)
@Inject
class PendingRestore(private val backupManager: BackupManager) {
    @Volatile
    private var payload: BackupPayload? = null

    fun stage(payload: BackupPayload) {
        this.payload = payload
    }

    fun clear() {
        payload = null
    }

    /** Whether a backup is staged. False after a process death: the payload lives only in memory. */
    val isStaged: Boolean get() = payload != null

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
