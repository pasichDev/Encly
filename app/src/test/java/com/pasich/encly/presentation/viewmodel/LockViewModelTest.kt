package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.testutil.answerCallback
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.anyCallback
import com.pasich.encly.testutil.eqValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The lock screen: PIN, recovery words and biometric unlock, and an unlock that lands in the background. */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    private lateinit var security: SecurityManager
    private lateinit var sessionLock: SessionLockManager
    private lateinit var viewModel: LockViewModel
    private val activity: FragmentActivity = mock(FragmentActivity::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        sessionLock = SessionLockManager(security)
        sessionLock.onStart(mock(LifecycleOwner::class.java))
        viewModel = LockViewModel(security, sessionLock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theRightPinUnlocks() = runTest {
        `when`(security.unlockWithPin(PIN)).thenReturn(VaultUnlockResult.SUCCESS)

        assertEquals(PinUnlockResult.SUCCESS, pin())
        assertFalse(viewModel.busy.value)
        assertFalse(sessionLock.locked.value)
    }

    @Test
    fun aWrongPinAndABrokenVaultAreToldApart() = runTest {
        `when`(security.unlockWithPin(PIN)).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        assertEquals(PinUnlockResult.WRONG_PIN, pin())

        `when`(security.unlockWithPin(PIN)).thenReturn(VaultUnlockResult.DB_ERROR)
        assertEquals(PinUnlockResult.DB_ERROR, pin())

        verify(security, never()).lock()
    }

    @Test
    fun aPinUnlockThatFinishesInTheBackgroundClosesTheVaultAgain() = runTest {
        `when`(security.unlockWithPin(PIN)).thenReturn(VaultUnlockResult.SUCCESS)
        sessionLock.onStop(mock(LifecycleOwner::class.java))

        assertEquals(PinUnlockResult.BACKGROUNDED, pin())
        verify(security).lock()
        assertTrue(sessionLock.locked.value)
    }

    @Test
    fun theRecoveryWordsUnlockAndAreWipedAfterwards() = runTest {
        `when`(security.unlockWithSeed(ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(VaultUnlockResult.SUCCESS)
        val words = WORDS.toCharArray()

        assertEquals(SeedUnlockResult.SUCCESS, seed(words))
        assertArrayEquals(CharArray(words.size), words)
    }

    @Test
    fun wrongRecoveryWordsAreReportedAndStillWiped() = runTest {
        `when`(security.unlockWithSeed(ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        val words = WORDS.toCharArray()

        assertEquals(SeedUnlockResult.WRONG_SEED, seed(words))
        assertArrayEquals(CharArray(words.size), words)

        `when`(security.unlockWithSeed(ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(VaultUnlockResult.DB_ERROR)
        assertEquals(SeedUnlockResult.DB_ERROR, seed(WORDS.toCharArray()))
    }

    @Test
    fun aSeedUnlockThatFinishesInTheBackgroundIsNotPublished() = runTest {
        `when`(security.unlockWithSeed(ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(VaultUnlockResult.SUCCESS)
        sessionLock.onStop(mock(LifecycleOwner::class.java))

        assertEquals(SeedUnlockResult.BACKGROUNDED, seed(WORDS.toCharArray()))
    }

    @Test
    fun aBiometricKeyUnlocksAndIsWiped() = runTest {
        val key = ByteArray(KEY_LENGTH) { 7 }
        answerCallback<ByteArray?>(key).`when`(security).requestBiometricKey(eqValue(activity), anyCallback())
        `when`(security.unlockWithRawKey(anyByteArray(), ArgumentMatchers.anyBoolean())).thenReturn(true)

        assertTrue(biometric())
        assertArrayEquals(ByteArray(KEY_LENGTH), key)
    }

    @Test
    fun aCancelledPromptOrARejectedKeyDoesNotUnlock() = runTest {
        answerCallback<ByteArray?>(null).`when`(security).requestBiometricKey(eqValue(activity), anyCallback())
        assertFalse(biometric())
        verify(security, never()).unlockWithRawKey(anyByteArray(), ArgumentMatchers.anyBoolean())

        answerCallback<ByteArray?>(ByteArray(KEY_LENGTH)).`when`(security)
            .requestBiometricKey(eqValue(activity), anyCallback())
        `when`(security.unlockWithRawKey(anyByteArray(), ArgumentMatchers.anyBoolean())).thenReturn(false)
        assertFalse(biometric())
    }

    @Test
    fun aSecondBiometricRequestWhileOneIsOpenIsIgnored() {
        // The prompt never answers: the first request stays in flight.
        viewModel.authenticateBiometric(activity) {}
        viewModel.authenticateBiometric(activity) {}

        verify(security, times(1)).requestBiometricKey(eqValue(activity), anyCallback())
    }

    @Test
    fun theStrategyDecidesWhichFormTheLockScreenShows() {
        `when`(security.authStrategy()).thenReturn(AuthStrategy.SEED_PHRASE_BIOMETRIC)
        assertTrue(viewModel.isSeedStrategy())
        `when`(security.authStrategy()).thenReturn(AuthStrategy.SEED_PHRASE)
        assertTrue(viewModel.isSeedStrategy())
        `when`(security.authStrategy()).thenReturn(AuthStrategy.PIN_BIOMETRIC)
        assertFalse(viewModel.isSeedStrategy())
        assertEquals(AuthStrategy.PIN_BIOMETRIC, viewModel.strategy())
    }

    @Test
    fun theLockScreenAsksTheVaultWhatItCanOffer() {
        `when`(security.isBiometricEnabled()).thenReturn(true)
        `when`(security.biometricAvailable()).thenReturn(false)
        `when`(security.hasRecoverySeed()).thenReturn(true)
        `when`(security.pinLockoutRemainingMillis()).thenReturn(LOCKOUT_MS)

        assertTrue(viewModel.biometricEnabled())
        assertFalse(viewModel.biometricAvailable())
        assertTrue(viewModel.recoveryAvailable())
        assertEquals(LOCKOUT_MS, viewModel.lockoutRemainingMillis())
    }

    private suspend fun pin(): PinUnlockResult {
        val result = CompletableDeferred<PinUnlockResult>()
        viewModel.authenticatePin(PIN) { result.complete(it) }
        return result.await()
    }

    private suspend fun seed(words: CharArray): SeedUnlockResult {
        val result = CompletableDeferred<SeedUnlockResult>()
        viewModel.authenticateSeed(words) { result.complete(it) }
        return result.await()
    }

    private suspend fun biometric(): Boolean {
        val result = CompletableDeferred<Boolean>()
        viewModel.authenticateBiometric(activity) { result.complete(it) }
        return result.await()
    }

    private companion object {
        const val PIN = "482915"
        const val WORDS = "one two three four five six seven eight nine ten eleven twelve"
        const val KEY_LENGTH = 32
        const val LOCKOUT_MS = 30_000L
    }
}
