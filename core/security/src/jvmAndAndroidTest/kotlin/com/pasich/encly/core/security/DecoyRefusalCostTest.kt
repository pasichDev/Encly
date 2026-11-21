package com.pasich.encly.core.security

import com.pasich.encly.testutil.DuressVaultFixture
import com.pasich.encly.testutil.DuressVaultFixture.Companion.DURESS_PIN
import com.pasich.encly.testutil.DuressVaultFixture.Companion.PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In a decoy session the PIN changes are refused, but not faster than the real vault does them: each
 * refusal runs one KDF run (one hardware-half call is the count of runs) over the PIN it was given, so the
 * pause the real vault shows is there too.
 */
class DecoyRefusalCostTest {
    private lateinit var vault: DuressVaultFixture

    @Before
    fun setUp() {
        vault = DuressVaultFixture(withDuressPin = true)
    }

    private fun openDecoy() {
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(DURESS_PIN.toCharArray()))
        assertTrue(vault.security.isDecoySession())
    }

    private fun openReal() {
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        assertTrue(!vault.security.isDecoySession())
    }

    /** The KDF runs [action] made. */
    private fun runs(action: () -> Unit): Int {
        val before = vault.factor.calls
        action()
        return vault.factor.calls - before
    }

    @Test
    fun refusingAPinChangeInADecoySessionRunsTheKdfOnceAndStillRefuses() {
        openDecoy()
        val pin = "246802".toCharArray()

        var result: PinChange? = null
        val runs = runs { result = vault.security.changePin(pin) }

        assertEquals(PinChange.FAILED, result)
        assertEquals(1, runs)
        assertTrue("the PIN is wiped", pin.all { it == '\u0000' })
    }

    @Test
    fun refusingAWipePinInADecoySessionRunsTheKdfOnceAndStillRefuses() {
        openDecoy()
        var result: WipePinChange? = null

        val runs = runs { result = vault.security.configureWipePin("246802".toCharArray()) }

        assertEquals(WipePinChange.FAILED, result)
        assertEquals(1, runs)
    }

    @Test
    fun refusingADuressPinInADecoySessionRunsTheKdfOnceAndStillRefuses() {
        openDecoy()
        var result: DuressPinChange? = null

        val runs = runs { result = vault.security.configureDuressPin("246802".toCharArray()) }

        assertEquals(DuressPinChange.FAILED, result)
        assertEquals(1, runs)
    }

    @Test
    fun theRealVaultsOwnWorkRunsTheSameOneKdfRun() {
        openReal()

        assertEquals(1, runs { vault.security.changePin("246802".toCharArray()) })
        assertEquals(1, runs { vault.security.configureWipePin("357913".toCharArray()) })
        assertEquals(1, runs { vault.security.configureDuressPin("468024".toCharArray()) })
    }

    @Test
    fun aDecoyRefusalWithAFailingKeyStoreStillRefusesTheSameWay() {
        openDecoy()
        vault.factor.failing = true

        assertEquals(PinChange.FAILED, vault.security.changePin("246802".toCharArray()))
        assertEquals(WipePinChange.FAILED, vault.security.configureWipePin("246802".toCharArray()))
        assertEquals(DuressPinChange.FAILED, vault.security.configureDuressPin("246802".toCharArray()))
    }

    @Test
    fun theRefusalsLeaveTheStateFileAsItWas() {
        openDecoy()
        val before = vault.file.readBytes()

        vault.security.changePin("246802".toCharArray())
        vault.security.configureWipePin("246802".toCharArray())
        vault.security.configureDuressPin("246802".toCharArray())

        assertTrue(before.contentEquals(vault.file.readBytes()))
    }
}
