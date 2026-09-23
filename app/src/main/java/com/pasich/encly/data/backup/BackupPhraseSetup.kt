package com.pasich.encly.data.backup

import com.pasich.encly.core.security.SecurityManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recovery phrase an export is sealed to. Backups have no separate passphrase (see
 * SECURITY.md), so a vault must have a recovery phrase, and its backup key, before it can
 * export. Every call needs an unlocked session.
 */
@Singleton
class BackupPhraseSetup @Inject constructor(private val securityManager: SecurityManager) {
    fun hasRecoveryPhrase(): Boolean = securityManager.hasRecoverySeed()

    /** A recovery-seed vault created before backup keys existed must confirm its words once. */
    fun needsConfirmation(): Boolean = securityManager.hasRecoverySeed() && !securityManager.hasBackupKey()

    /** [phrase] must be this vault's recovery phrase, normalized; the caller wipes it. */
    fun confirm(phrase: CharArray): Boolean = securityManager.createBackupKey(phrase)

    /** New 12 words for a vault without a recovery phrase. The caller must wipe them. */
    fun generate(): CharArray = securityManager.generateMnemonicCode()

    /** Adds [phrase] as the vault's recovery phrase and backup key; the caller wipes it. */
    fun add(phrase: CharArray): Boolean = securityManager.addRecoverySeed(phrase)
}
