package com.pasich.encly.core.security

import com.pasich.encly.testutil.DuressVaultFixture
import com.pasich.encly.testutil.DuressVaultFixture.Companion.DURESS_PIN
import com.pasich.encly.testutil.DuressVaultFixture.Companion.OTHER_DURESS_PIN
import com.pasich.encly.testutil.DuressVaultFixture.Companion.PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Setting the duress PIN killed at each stage (issue #131). The set-up has two steps: `onNewVault`
 * (deletes a decoy database nothing can open any more), then one atomic write of the duress slot
 * and `decoy.key` together. The decoy database itself is created later, by the first unlock with
 * the duress PIN. After a kill and a restart the real vault must open with the real PIN, and the
 * duress PIN must either open the decoy vault fully or be a plain wrong PIN, never in between.
 */
class DuressSetupInterruptionTest {
    private class ProcessDeath : RuntimeException("process killed")

    private fun DuressVaultFixture.unlockReal(): VaultUnlockResult {
        security.lock()
        return security.unlockWithPin(PIN.toCharArray())
    }

    private fun DuressVaultFixture.assertRealVaultOpensWithItsOwnKey() {
        assertEquals(VaultUnlockResult.SUCCESS, unlockReal())
        assertEquals(VaultKind.PRIMARY, db.open)
        assertEquals(bootstrap.toList(), db.opens.last().key)
        assertFalse(security.isDecoySession())
    }

    private fun DuressVaultFixture.assertDuressPinIsJustWrong() {
        security.lock()
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertFalse(security.isDatabaseUnlocked())
        assertFalse("no decoy database was made", VaultKind.DECOY in db.files)
        assertTrue(db.opens.none { it.vault == VaultKind.DECOY })
    }

    /** The set-up that was killed can be done again, and then works fully. */
    private fun DuressVaultFixture.assertSetUpCanBeRepeated() {
        assertEquals(VaultUnlockResult.SUCCESS, unlockReal())
        assertEquals(DuressPinChange.SET_NEW_VAULT, security.configureDuressPin(DURESS_PIN.toCharArray()))
        security.lock()
        assertEquals(VaultUnlockResult.SUCCESS, security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertEquals(VaultKind.DECOY, db.open)
        assertTrue(security.isDecoySession())
    }

    // --- the first duress PIN -------------------------------------------------------------

    @Test
    fun killedInsideOnNewVaultLeavesNoDuressPin() {
        val vault = DuressVaultFixture()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        vault.db.beforeWipeIfClosed = { throw ProcessDeath() }

        assertThrows(ProcessDeath::class.java) { vault.security.configureDuressPin(DURESS_PIN.toCharArray()) }
        vault.restart()
        vault.db.beforeWipeIfClosed = null

        vault.assertRealVaultOpensWithItsOwnKey()
        assertFalse(vault.security.hasDuressPin())
        vault.assertDuressPinIsJustWrong()
        vault.assertSetUpCanBeRepeated()
    }

    @Test
    fun killedBeforeTheSlotWasRenamedLeavesNoDuressPin() {
        val vault = DuressVaultFixture()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        vault.store.beforeRename = { throw ProcessDeath() }

        assertThrows(ProcessDeath::class.java) { vault.security.configureDuressPin(DURESS_PIN.toCharArray()) }
        vault.restart()

        vault.assertRealVaultOpensWithItsOwnKey()
        assertFalse(vault.security.hasDuressPin())
        vault.assertDuressPinIsJustWrong()
        vault.assertSetUpCanBeRepeated()
    }

    @Test
    fun aWriteThatFailsWithAnIoErrorLeavesNoDuressPinInTheSameProcessToo() {
        val vault = DuressVaultFixture()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        vault.store.beforeRename = { throw IOException("disk full") }

        assertEquals(DuressPinChange.FAILED, vault.security.configureDuressPin(DURESS_PIN.toCharArray()))
        vault.store.beforeRename = null

        assertFalse(vault.security.hasDuressPin())
        vault.assertDuressPinIsJustWrong()
        vault.assertRealVaultOpensWithItsOwnKey()
        vault.assertSetUpCanBeRepeated()
    }

    @Test
    fun killedAfterTheSlotWasWrittenTheDuressPinOpensTheDecoyVaultFully() {
        val vault = DuressVaultFixture()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, vault.security.configureDuressPin(DURESS_PIN.toCharArray()))
        // The process dies here: no decoy database exists yet, it is made by the first duress unlock.
        assertFalse(VaultKind.DECOY in vault.db.files)
        vault.restart()

        vault.assertRealVaultOpensWithItsOwnKey()
        assertTrue(vault.security.hasDuressPin())
        vault.security.lock()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertTrue(vault.security.isDecoySession())
        assertEquals(VaultKind.DECOY, vault.db.open)
        assertTrue("made on the first unlock", vault.db.opens.last().allowCreate)
        assertNotEquals("its own key", vault.bootstrap.toList(), vault.db.opens.last().key)
    }

    @Test
    fun aDecoyDatabaseThatStoppedHalfWayIsOpenedWithTheSameKeyOnTheNextTry() {
        val vault = DuressVaultFixture(withDuressPin = true)
        vault.db.failNextCreate = true

        assertEquals(VaultUnlockResult.DB_ERROR, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        val firstKey = vault.db.opens.last().key
        assertFalse(vault.security.isDatabaseUnlocked())
        assertFalse("no fall back to the real vault", vault.db.opens.any { it.vault == VaultKind.PRIMARY })
        vault.restart()

        // The real vault is untouched, and the next duress unlock opens the file that is there.
        vault.assertRealVaultOpensWithItsOwnKey()
        vault.security.lock()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertFalse("the file is there now", vault.db.opens.last().allowCreate)
        assertEquals("the same decoy key", firstKey, vault.db.opens.last().key)
        assertTrue(vault.security.isDecoySession())
    }

    // --- changing an existing duress PIN --------------------------------------------------

    private fun DuressVaultFixture.decoyKeyOfFirstUnlock(): List<Byte> {
        assertEquals(VaultUnlockResult.SUCCESS, security.unlockWithPin(DURESS_PIN.toCharArray()))
        val key = db.opens.last().key
        security.lock()
        return key
    }

    @Test
    fun changingTheDuressPinKilledBeforeTheRenameKeepsTheOldOneAndTheSameDecoyVault() {
        val vault = DuressVaultFixture(withDuressPin = true)
        val decoyKey = vault.decoyKeyOfFirstUnlock()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        vault.store.beforeRename = { throw ProcessDeath() }

        assertThrows(ProcessDeath::class.java) { vault.security.configureDuressPin(OTHER_DURESS_PIN.toCharArray()) }
        vault.restart()

        vault.assertRealVaultOpensWithItsOwnKey()
        vault.security.lock()
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, vault.security.unlockWithPin(OTHER_DURESS_PIN.toCharArray()))
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertEquals("the same decoy vault", decoyKey, vault.db.opens.last().key)
    }

    @Test
    fun changingTheDuressPinKilledAfterTheRenameOpensTheSameDecoyVaultWithTheNewPinOnly() {
        val vault = DuressVaultFixture(withDuressPin = true)
        val decoyKey = vault.decoyKeyOfFirstUnlock()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        assertEquals(DuressPinChange.SET, vault.security.configureDuressPin(OTHER_DURESS_PIN.toCharArray()))
        vault.restart()

        vault.assertRealVaultOpensWithItsOwnKey()
        vault.security.lock()
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(OTHER_DURESS_PIN.toCharArray()))
        assertEquals(decoyKey, vault.db.opens.last().key)
    }

    @Test
    fun anOrphanedDecoyDatabaseOfARemovedDuressPinIsReplacedBySettingItAgain() {
        // Turning the duress PIN off forgets the key first and deletes the file second; a kill in between
        // leaves a file nothing can open. It is no half state: the PIN is plainly wrong, the real vault
        // opens, and the next set-up deletes the leftover and starts a new, empty decoy vault.
        val vault = DuressVaultFixture(withDuressPin = true)
        val oldKey = vault.decoyKeyOfFirstUnlock()
        assertTrue(VaultKind.DECOY in vault.db.files)
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        vault.db.beforeWipeIfClosed = { throw ProcessDeath() }

        assertThrows(ProcessDeath::class.java) { vault.security.removeDuressPin() }
        vault.restart()
        vault.db.beforeWipeIfClosed = null

        assertTrue("the file is left", VaultKind.DECOY in vault.db.files)
        vault.assertRealVaultOpensWithItsOwnKey()
        assertFalse(vault.security.hasDuressPin())
        vault.security.lock()
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))

        assertEquals(VaultUnlockResult.SUCCESS, vault.unlockReal())
        assertEquals(DuressPinChange.SET_NEW_VAULT, vault.security.configureDuressPin(DURESS_PIN.toCharArray()))
        assertFalse("the leftover went", VaultKind.DECOY in vault.db.files)
        vault.security.lock()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertNotEquals("a new decoy vault", oldKey, vault.db.opens.last().key)
    }
}
