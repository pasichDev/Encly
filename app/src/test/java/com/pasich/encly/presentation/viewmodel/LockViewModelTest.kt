package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.testutil.MockActivity
import com.pasich.encly.testutil.answerCallback
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.anyCallback
import com.pasich.encly.testutil.anyCharArray
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
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/** The lock screen: PIN, recovery words and biometric unlock, and an unlock that lands in the background. */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    private lateinit var security: SecurityManager
    private lateinit var sessionLock: SessionLockManager
    private lateinit var viewModel: LockViewModel
    private val host = MockActivity()
    private val activity: FragmentActivity = host.activity

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
        `when`(security.unlockWithPin(PIN.toCharArray())).thenReturn(VaultUnlockResult.SUCCESS)

        assertEquals(PinUnlockResult.SUCCESS, pin())
        assertFalse(viewModel.busy.value)
        assertFalse(sessionLock.locked.value)
    }

    @Test
    fun aWrongPinAndABrokenVaultAreToldApart() = runTest {
        `when`(security.unlockWithPin(PIN.toCharArray())).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        assertEquals(PinUnlockResult.WRONG_PIN, pin())

        `when`(security.unlockWithPin(PIN.toCharArray())).thenReturn(VaultUnlockResult.DB_ERROR)
        assertEquals(PinUnlockResult.DB_ERROR, pin())

        verify(security, never()).lock()
    }

    @Test
    fun aPinUnlockThatFinishesInTheBackgroundClosesTheVaultAgain() = runTest {
        `when`(security.unlockWithPin(PIN.toCharArray())).thenReturn(VaultUnlockResult.SUCCESS)
        sessionLock.onStop(mock(LifecycleOwner::class.java))

        assertEquals(PinUnlockResult.BACKGROUNDED, pin())
        verify(security).lock()
        assertTrue(sessionLock.locked.value)
    }

    @Test
    fun anUnlockThatFinishesAfterTheScreenIsGoneClosesTheVaultAgain() {
        // The PIN check (KDF) is running when the lock screen's ViewModel is cleared.
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        `when`(security.unlockWithPin(anyCharArray())).thenAnswer {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            VaultUnlockResult.SUCCESS
        }
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        val store = ViewModelStore()
        val owned = ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = viewModel as T
            },
        )[LockViewModel::class]
        var published: PinUnlockResult? = null

        owned.authenticatePin(PIN.toCharArray()) { published = it }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        store.clear()
        release.countDown()

        verify(security, timeout(5_000)).lock()
        assertTrue(sessionLock.locked.value)
        assertEquals("nothing navigates into the vault", null, published)
    }

    @Test
    fun aLostPinKeyAndALockoutAreReported() = runTest {
        `when`(security.unlockWithPin(PIN.toCharArray())).thenReturn(VaultUnlockResult.PIN_KEY_LOST)
        assertEquals(PinUnlockResult.KEY_LOST, pin())

        `when`(security.unlockWithPin(PIN.toCharArray())).thenReturn(VaultUnlockResult.LOCKED_OUT)
        assertEquals(PinUnlockResult.LOCKED_OUT, pin())
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
        assertTrue(viewModel.biometricInFlight)
    }

    @Test
    fun aRotationDuringThePromptReleasesItForTheRecreatedScreen() {
        viewModel.authenticateBiometric(activity) {}
        assertTrue(viewModel.biometricInFlight)

        // androidx.biometric drops the answer of a prompt whose activity is gone: without this the
        // ViewModel, which outlives the rotation, would never prompt again.
        host.destroy()

        assertFalse(viewModel.biometricInFlight)
        val recreated = MockActivity().activity
        viewModel.authenticateBiometric(recreated) {}
        verify(security).requestBiometricKey(eqValue(recreated), anyCallback())
    }

    @Test
    fun aLateAnswerFromTheDestroyedActivityIsDroppedAndItsKeyWiped() {
        val answer = arrayOfNulls<(ByteArray?) -> Unit>(1)
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            answer[0] = invocation.arguments.last() as (ByteArray?) -> Unit
            null
        }.`when`(security).requestBiometricKey(eqValue(activity), anyCallback())
        val results = mutableListOf<Boolean>()
        viewModel.authenticateBiometric(activity) { results += it }
        host.destroy()

        val key = ByteArray(KEY_LENGTH) { 7 }
        answer[0]!!(key)

        assertTrue(results.isEmpty())
        assertArrayEquals(ByteArray(KEY_LENGTH), key)
        verify(security, never()).unlockWithRawKey(anyByteArray(), ArgumentMatchers.anyBoolean())
    }

    @Test
    fun aDestroyedActivityGetsNoPrompt() {
        host.destroy()

        viewModel.authenticateBiometric(activity) {}

        verify(security, never()).requestBiometricKey(eqValue(activity), anyCallback())
        assertFalse(viewModel.biometricInFlight)
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
        viewModel.authenticatePin(PIN.toCharArray()) { result.complete(it) }
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
