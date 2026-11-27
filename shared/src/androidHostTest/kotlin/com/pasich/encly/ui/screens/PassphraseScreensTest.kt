package com.pasich.encly.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.lock_back_to_passphrase
import com.pasich.encly.core.resources.lock_passphrase_subtitle
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.lock_wrong_passphrase
import com.pasich.encly.core.resources.onboarding_get_started
import com.pasich.encly.core.resources.onboarding_passphrase_again_title
import com.pasich.encly.core.resources.onboarding_passphrase_title
import com.pasich.encly.core.resources.onboarding_recovery_create
import com.pasich.encly.core.resources.onboarding_recovery_passphrase_title
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.resources.passphrase_mismatch_retry
import com.pasich.encly.core.resources.passphrase_suggest
import com.pasich.encly.core.resources.passphrase_suggest_note
import com.pasich.encly.core.resources.passphrase_too_short
import com.pasich.encly.core.resources.passphrase_too_simple
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.Mnemonic
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.onboarding.OnboardingScreen
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.testutil.FakePassphrasePolicy
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`

/** What the screens that take or set the secret need to be tested where the secret is a passphrase. */
@OptIn(ExperimentalTestApi::class)
internal abstract class PassphraseScreenTest : ComposeScreenTest() {
    protected val app by lazy { TestApp() }
    protected val policy = FakePassphrasePolicy()
    protected val good: String = FakePassphrasePolicy.GOOD

    protected fun passphraseLock(): LockViewModel =
        LockViewModel(app.security, app.sessionLock, CoroutineScope(SupervisorJob() + Dispatchers.Main), policy)

    protected fun passphraseSettings(): SecuritySettingsViewModel = SecuritySettingsViewModel(
        app.security,
        KeyboardPrivacy(InMemoryKeyValueStore()),
        AutoLock(InMemoryKeyValueStore()),
        app.screenshots,
        policy,
    )

    protected fun passphraseOnboarding(): OnboardingViewModel = OnboardingViewModel(
        OnboardingUseCase(app.security, app.backupManager, PendingRestore(app.backupManager)),
        app.documents,
        policy,
    )

    private val label get() = str(Res.string.passphrase_label)

    /** The passphrase fields on show: one, except while a step slides out and the next slides in. */
    private fun fields() = rule.onAllNodesWithContentDescription(label)

    protected fun typePassphrase(text: String) {
        waitFor { fields().fetchSemanticsNodes().size == 1 }
        fields()[0].performTextInput(text)
        rule.waitForIdle()
    }

    /** What the field holds, as the semantics report it (the masked text is the screen reader's, not the typed). */
    protected fun fieldIsEmpty(): Boolean = fields()[0].fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.EditableText)?.text.orEmpty().isEmpty()

    protected fun press(text: String) {
        rule.onNode(hasText(text) and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    protected fun pressContinue() = press(str(Res.string.action_continue))

    protected fun enterPassphrase(text: String, button: String = str(Res.string.action_continue)) {
        typePassphrase(text)
        press(button)
    }

    protected fun assertNoKeypad() {
        assertEquals(0, rule.onAllNodes(hasText("5") and hasClickAction()).fetchSemanticsNodes().size)
    }
}

/** The lock screen where the secret is a passphrase. */
@OptIn(ExperimentalTestApi::class)
internal class PassphraseLockScreenTest : PassphraseScreenTest() {
    private fun show(recovery: Boolean = true) {
        `when`(app.security.hasRecoverySeed()).thenReturn(recovery)
        setNavScreen(viewModels(passphraseLock()), route = NavRoutes.LockRoute.name) { nav -> LockScreen(nav) }
    }

    private val unlock get() = str(Res.string.lock_unlock)

    @Test
    fun itShowsAFieldAndNoKeypad() {
        show()

        rule.onNodeWithText(str(Res.string.lock_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.lock_passphrase_subtitle)).assertIsDisplayed()
        assertNoKeypad()
    }

    @Test
    fun unlockIsOffUntilSomethingIsTypedAndNothingShowsTheLength() {
        show()
        rule.onNodeWithText(unlock).assertIsNotEnabled()

        typePassphrase("x")

        rule.onNodeWithText(unlock).assertIsEnabled()
    }

    @Test
    fun theRightPassphraseOpensTheNotes() {
        `when`(app.security.unlockWithPin(anyCharArray())).thenReturn(VaultUnlockResult.SUCCESS)
        show()

        enterPassphrase(good, unlock)

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
    }

    @Test
    fun aWrongPassphraseSaysSoAndLeavesTheFieldEmpty() {
        `when`(app.security.unlockWithPin(anyCharArray())).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        show()

        enterPassphrase("not the passphrase", unlock)

        waitForText(str(Res.string.lock_wrong_passphrase))
        assertTrue(fieldIsEmpty())
        assertEquals(NavRoutes.LockRoute.name, currentRoute())
    }

    @Test
    fun editingTheNextAttemptClearsTheError() {
        `when`(app.security.unlockWithPin(anyCharArray())).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        show()
        enterPassphrase("not the passphrase", unlock)
        waitForText(str(Res.string.lock_wrong_passphrase))

        typePassphrase("n")

        assertEquals(0, countText(str(Res.string.lock_wrong_passphrase)))
    }

    @Test
    fun aLockoutDisablesTheFieldAndTheButtonAndShowsNoWrongPassphrase() {
        var lockedOut = false
        `when`(app.security.pinLockoutRemainingMillis()).thenAnswer { if (lockedOut) 30_000L else 0L }
        `when`(app.security.unlockWithPin(anyCharArray())).thenAnswer {
            lockedOut = true
            VaultUnlockResult.INVALID_CREDENTIAL
        }
        show()

        enterPassphrase("not the passphrase", unlock)

        waitFor {
            rule.onAllNodes(hasText(unlock) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithContentDescription(str(Res.string.passphrase_label)).assertIsNotEnabled()
    }

    @Test
    fun theMainWipeAndDuressPassphrasesLookTheSameUntilTheAnswerAndAfterAWrongOne() {
        val received = mutableListOf<String>()
        `when`(app.security.unlockWithPin(anyCharArray())).thenAnswer {
            received += String(it.getArgument<CharArray>(0))
            VaultUnlockResult.INVALID_CREDENTIAL
        }
        show()
        val snapshots = listOf("main passphrase here", "wipe passphrase here", "duress passphrase here").map { typed ->
            enterPassphrase(typed, unlock)
            waitForText(str(Res.string.lock_wrong_passphrase))
            assertTrue(fieldIsEmpty())
            visibleTexts().map(::textOf) + liveRegions()
        }

        assertEquals(listOf("main passphrase here", "wipe passphrase here", "duress passphrase here"), received)
        assertEquals(snapshots[0], snapshots[1])
        assertEquals(snapshots[0], snapshots[2])
    }

    @Test
    fun theFieldIsNamedOnceNotByItsLabelAndItsDescription() {
        show()

        assertEquals(0, countText(str(Res.string.passphrase_label)))
        waitFor {
            rule.onAllNodesWithContentDescription(str(Res.string.passphrase_label)).fetchSemanticsNodes().size ==
                1
        }
    }

    /** What a screen reader is told as it appears: the live regions on show. */
    private fun liveRegions(): List<String> = rule.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().map(::textOf)

    @Test
    fun theRecoveryLinkLeadsToTheWordsAndBackToThePassphrase() {
        show()

        rule.onNodeWithText(str(Res.string.lock_use_recovery_phrase)).performClick()
        rule.onNodeWithText(str(Res.string.lock_back_to_passphrase)).performClick()

        rule.onNodeWithText(str(Res.string.lock_passphrase_subtitle)).assertIsDisplayed()
    }

    @Test
    fun withoutARecoveryPhraseThereIsNoRecoveryLink() {
        show(recovery = false)

        assertEquals(0, countText(str(Res.string.lock_use_recovery_phrase)))
    }
}

/** First run where the secret is a passphrase: choose it, see why one is refused, type it again. */
@OptIn(ExperimentalTestApi::class)
internal class PassphraseOnboardingScreenTest : PassphraseScreenTest() {
    private fun show() {
        app.withWorkingVault(String(Mnemonic.generate()))
        setScreen(viewModels(passphraseOnboarding(), app.authSetup())) {
            OnboardingScreen(onComplete = {})
        }
        press(str(Res.string.onboarding_get_started))
    }

    @Test
    fun theStepAsksForAPassphraseInAFieldWithoutAKeypad() {
        show()

        rule.onNodeWithText(str(Res.string.onboarding_passphrase_title)).assertIsDisplayed()
        assertNoKeypad()
        rule.onNodeWithText(str(Res.string.action_continue)).assertIsNotEnabled()
    }

    @Test
    fun aTooShortPassphraseIsRefusedWithTheReasonAndStaysTyped() {
        show()

        enterPassphrase("short")

        waitForText(str(Res.string.passphrase_too_short))
        assertTrue(!fieldIsEmpty())
    }

    @Test
    fun aTooSimpleOneSaysWhy() {
        show()

        enterPassphrase("123456789012")

        waitForText(str(Res.string.passphrase_too_simple))
    }

    @Test
    fun theSuggestionFillsTheFieldAndSaysItIsNotTheRecoveryPhrase() {
        show()

        press(str(Res.string.passphrase_suggest))

        rule.onNodeWithText(str(Res.string.passphrase_suggest_note)).assertIsDisplayed()
        assertTrue(!fieldIsEmpty())
        assertEquals(1, policy.suggestCalls)
        pressContinue()
        waitForText(str(Res.string.onboarding_passphrase_again_title))
        assertEquals(0, countText(str(Res.string.passphrase_suggest)))
    }

    @Test
    fun aMismatchOnTheSecondEntryStartsOver() {
        show()
        enterPassphrase(good)
        waitForText(str(Res.string.onboarding_passphrase_again_title))

        enterPassphrase("correct horse batterz")

        waitForText(str(Res.string.passphrase_mismatch_retry))
        waitForText(str(Res.string.onboarding_passphrase_title))
    }

    @Test
    fun theSameTwiceMovesOnToTheRecoveryPhrase() {
        show()
        enterPassphrase(good)
        waitForText(str(Res.string.onboarding_passphrase_again_title))

        enterPassphrase(good)

        waitForText(str(Res.string.onboarding_recovery_create))
        waitForText(str(Res.string.onboarding_recovery_passphrase_title))
    }
}
