package com.pasich.encly.core.security

import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.database.VaultFile
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureDatabaseVaultTest {
    private val manager = mock(SecureDatabaseManager::class.java)
    private val vault = SecureDatabaseVault(manager)
    private val dek = ByteArray(32) { it.toByte() }

    @Test
    fun theStateQuestionsAreTheManagersAnswers() {
        `when`(manager.isDatabaseUnlocked()).thenReturn(true)
        `when`(manager.hasEncryptedDatabase(VaultFile.PRIMARY)).thenReturn(true)
        `when`(manager.hasEncryptedDatabase(VaultFile.DECOY)).thenReturn(false)

        assertTrue(vault.isDatabaseUnlocked())
        assertTrue(vault.hasEncryptedDatabase(VaultKind.PRIMARY))
        assertFalse(vault.hasEncryptedDatabase(VaultKind.DECOY))
    }

    @Test
    fun aVaultKindNamesItsDatabaseFile() {
        `when`(manager.unlockDatabase(dek, true, VaultFile.DECOY)).thenReturn(true)

        assertTrue(vault.unlockDatabase(dek, allowCreate = true, vault = VaultKind.DECOY))
        verify(manager).unlockDatabase(dek, true, VaultFile.DECOY)
    }

    @Test
    fun unlockingWithoutAKindOpensThePrimaryVault() {
        `when`(manager.unlockDatabase(dek, false)).thenReturn(true)

        assertTrue(vault.unlockDatabase(dek, allowCreate = false))
        verify(manager).unlockDatabase(dek, false)
    }

    @Test
    fun resetAndWipeGoToTheManager() {
        vault.reset()
        vault.wipe()

        verify(manager).reset()
        verify(manager).wipe()
    }

    @Test
    fun wipingAClosedVaultMapsTheKindAndNoneMeansEveryFile() {
        `when`(manager.wipeIfClosed(VaultFile.PRIMARY)).thenReturn(true)
        `when`(manager.wipeIfClosed(null)).thenReturn(false)

        assertTrue(vault.wipeIfClosed(VaultKind.PRIMARY))
        assertEquals(false, vault.wipeIfClosed(null))
    }
}
