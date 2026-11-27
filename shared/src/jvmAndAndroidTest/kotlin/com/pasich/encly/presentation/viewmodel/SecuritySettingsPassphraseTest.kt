package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_current_wrong
import com.pasich.encly.core.resources.passphrase_update_failed
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.core.security.ScreenshotAllowance
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.testutil.FakePassphrasePolicy
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Settings → Security where the secret is a passphrase: the kind, the rule for a new one, the wording of errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecuritySettingsPassphraseTest {
    private lateinit var security: SecurityManager
    private lateinit var passphrase: SecuritySettingsViewModel
    private lateinit var pin: SecuritySettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.getSettingsAuth()).thenReturn(AuthSettings(AuthType.PIN, false, false))
        `when`(security.biometricStatus()).thenReturn(BiometricStatus.UNAVAILABLE)
        passphrase = build(FakePassphrasePolicy())
        pin = build(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(policy: FakePassphrasePolicy?): SecuritySettingsViewModel {
        val keyboard = KeyboardPrivacy(InMemoryKeyValueStore())
        val autoLock = AutoLock(InMemoryKeyValueStore())
        val screenshots = ScreenshotAllowance()
        val viewModel = if (policy == null) {
            SecuritySettingsViewModel(security, keyboard, autoLock, screenshots)
        } else {
            SecuritySettingsViewModel(security, keyboard, autoLock, screenshots, policy)
        }
        runBlocking { viewModel.uiState.first { it.loaded } }
        return viewModel
    }

    @Test
    fun theKindIsThePolicysAndAPinWithoutOne() {
        assertEquals(SecretKind.PASSWORD, passphrase.secretKind)
        assertEquals(SecretKind.PIN, pin.secretKind)
    }

    @Test
    fun aNewPassphraseIsCheckedAgainstTheRuleAndAPinIsNot() {
        assertEquals(SecretRefusal.TOO_SHORT, passphrase.refusalOf("short".toCharArray()))
        assertNull(passphrase.refusalOf(FakePassphrasePolicy.GOOD.toCharArray()))
        assertNull(pin.refusalOf("1".toCharArray()))
    }

    @Test
    fun twoEntriesAreComparedByTheirCanonicalForm() {
        assertTrue(passphrase.sameSecret("correct horse".toCharArray(), "correct horse".toCharArray()))
        assertFalse(passphrase.sameSecret("correct horse".toCharArray(), "correct house".toCharArray()))
        assertTrue(pin.sameSecret("123456".toCharArray(), "123456".toCharArray()))
    }

    @Test
    fun aPassphraseIsSuggestedAndAPinIsNot() {
        assertEquals(FakePassphrasePolicy.SUGGESTION, String(passphrase.suggestSecret()!!))
        assertNull(pin.suggestSecret())
    }

    @Test
    fun aWrongCurrentPassphraseIsReportedInThePassphraseWording() = runTest {
        `when`(security.verifyPinResult("nope nope nope".toCharArray())).thenReturn(PinCheck.WRONG)

        val ok = CompletableDeferred<PinCheck>()
        passphrase.verifyCurrentPin("nope nope nope".toCharArray()) { ok.complete(it) }

        assertEquals(PinCheck.WRONG, ok.await())
        assertEquals(UiText.of(Res.string.passphrase_current_wrong), passphrase.uiState.value.error)
    }

    @Test
    fun aWrongCurrentPinKeepsTheOldWording() = runTest {
        `when`(security.verifyPinResult("111111".toCharArray())).thenReturn(PinCheck.WRONG)

        val ok = CompletableDeferred<PinCheck>()
        pin.verifyCurrentPin("111111".toCharArray()) { ok.complete(it) }

        assertEquals(PinCheck.WRONG, ok.await())
        assertEquals(UiText.of(Res.string.pin_current_wrong), pin.uiState.value.error)
    }

    @Test
    fun aFailedChangeIsReportedInThePassphraseWording() = runTest {
        `when`(security.changePin(anyCharArray())).thenReturn(PinChange.FAILED)

        val result = CompletableDeferred<PinChange>()
        passphrase.activationPinAuth(FakePassphrasePolicy.GOOD.toCharArray()) { result.complete(it) }

        assertEquals(PinChange.FAILED, result.await())
        assertEquals(UiText.of(Res.string.passphrase_update_failed), passphrase.uiState.value.error)
    }
}
