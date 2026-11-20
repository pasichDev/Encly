package com.pasich.encly.domain.usecase

import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.PendingRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** The vault's mode is chosen by passing a recovery seed or not, and the seed is wiped. */
class CreateVaultTest {
    private val security = mock(SecurityManager::class.java)
    private val useCase = OnboardingUseCase(security, mock(BackupManager::class.java), mock(PendingRestore::class.java))
    private var seedSeen: String? = "not called"

    private fun anyChars(): CharArray? = ArgumentMatchers.any()

    private fun vaultCreation(succeeds: Boolean) {
        `when`(security.initializeNewVault(anyChars())).thenAnswer {
            seedSeen = it.getArgument<CharArray?>(0)?.let(::String)
            succeeds
        }
    }

    @Test
    fun aUserManagedVaultGetsTheSeedWhichIsThenWiped() {
        vaultCreation(succeeds = true)
        val seed = "abandon ability able".toCharArray()

        val result = useCase.createVault(seed)

        assertTrue(result.isSuccess)
        assertEquals("abandon ability able", seedSeen)
        assertTrue(seed.all { it == 0.toChar() })
    }

    @Test
    fun anAutoManagedVaultHasNoRecoverySeed() {
        vaultCreation(succeeds = true)

        assertTrue(useCase.createVault(recoverySeed = null).isSuccess)
        assertNull(seedSeen)
    }

    @Test
    fun aVaultThatCannotBeCreatedIsAFailureAndTheSeedIsStillWiped() {
        vaultCreation(succeeds = false)
        val seed = "abandon".toCharArray()

        assertTrue(useCase.createVault(seed).isFailure)
        assertTrue(seed.all { it == 0.toChar() })
    }
}
