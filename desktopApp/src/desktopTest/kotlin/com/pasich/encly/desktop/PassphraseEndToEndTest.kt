package com.pasich.encly.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.pasich.encly.core.keystore.DesktopPasswordPolicy
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.lock_recover_access
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.lock_wrong_passphrase
import com.pasich.encly.core.resources.notes_title
import com.pasich.encly.core.resources.onboarding_get_started
import com.pasich.encly.core.resources.onboarding_passphrase_again_title
import com.pasich.encly.core.resources.onboarding_ready_open
import com.pasich.encly.core.resources.onboarding_recovery_skip_passphrase
import com.pasich.encly.core.resources.passphrase_change_confirm_title
import com.pasich.encly.core.resources.passphrase_create_title
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.resources.passphrase_suggest
import com.pasich.encly.core.resources.recovery_choice_body_passphrase
import com.pasich.encly.core.resources.recovery_set_new_passphrase
import com.pasich.encly.core.resources.recovery_word_cell
import com.pasich.encly.core.security.Mnemonic
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.platform.AppWindow
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app with the real password policy (Argon2id): a suggested passphrase is accepted at onboarding, the app locks,
 * the lock screen refuses a wrong passphrase and opens with the right one. Few derivations: each costs about a second.
 */
@OptIn(ExperimentalTestApi::class)
class PassphraseEndToEndTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun text(res: StringResource): String = runBlocking { getString(res) }

    private fun ComposeUiTest.waitForText(res: StringResource) {
        val shown = text(res)
        waitUntil(timeoutMillis = LONG_WAIT_MS) { onAllNodesWithText(shown).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.press(res: StringResource) {
        waitForText(res)
        onNode(hasText(text(res)) and hasClickAction()).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.field() = run {
        val label = text(Res.string.passphrase_label)
        waitUntil(timeoutMillis = LONG_WAIT_MS) {
            onAllNodesWithContentDescription(label).fetchSemanticsNodes().size ==
                1
        }
        onAllNodesWithContentDescription(label)[0]
    }

    private fun ComposeUiTest.typed(): String =
        field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun aSuggestedPassphraseSetsUpTheVaultAndTheLockScreenTakesOnlyIt() = runComposeUiTest {
        val environment = testEnvironment(temp.root.toPath(), secretPolicy = DesktopPasswordPolicy())
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(environment)
        graph.lifecycle.lifecycle.addObserver(graph.sessionLockManager)
        assertEquals(SecretKind.PASSWORD, graph.securityManager.secretKind)
        setContent { DesktopApp(graph, AppWindow.None) }

        press(Res.string.onboarding_get_started)
        press(Res.string.passphrase_suggest)
        val passphrase = typed()
        assertEquals(5, passphrase.split(' ').size)
        press(Res.string.action_continue)
        waitForText(Res.string.onboarding_passphrase_again_title)
        field().performTextInput(passphrase)
        press(Res.string.action_continue)
        press(Res.string.onboarding_recovery_skip_passphrase)
        press(Res.string.onboarding_ready_open)
        waitForText(Res.string.notes_title)

        runOnIdle { graph.sessionLockManager.lockNow() }
        waitForText(Res.string.lock_unlock)
        field().performTextInput("not the passphrase at all")
        press(Res.string.lock_unlock)
        waitForText(Res.string.lock_wrong_passphrase)
        assertTrue(typed().isEmpty())

        field().performTextInput(passphrase)
        press(Res.string.lock_unlock)
        waitForText(Res.string.notes_title)
    }

    @Test
    fun theFirstEntryTypedPrecomposedAndTheConfirmationDecomposedAreTheSamePassphrase() = runComposeUiTest {
        val environment = testEnvironment(temp.root.toPath(), secretPolicy = DesktopPasswordPolicy())
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(environment)
        setContent { DesktopApp(graph, AppWindow.None) }

        press(Res.string.onboarding_get_started)
        waitForText(Res.string.passphrase_suggest)
        field().performTextInput("caf\u00e9 au lait \u00e7a va")
        press(Res.string.action_continue)
        waitForText(Res.string.onboarding_passphrase_again_title)
        field().performTextInput("cafe\u0301 au lait c\u0327a va")
        press(Res.string.action_continue)

        waitForText(Res.string.onboarding_recovery_skip_passphrase)
    }

    @Test
    fun afterTheRecoveryPhraseAnewPassphraseIsAskedForAndTakenWithoutTheOldOne() = runComposeUiTest {
        val environment = testEnvironment(temp.root.toPath(), secretPolicy = DesktopPasswordPolicy())
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(environment)
        val security = graph.securityManager
        val words = String(Mnemonic.generate())
        check(security.initializeNewVault(words.toCharArray()))
        check(security.configurePin("correct horse battery".toCharArray()))
        check(security.finishInitialSetup())
        graph.lifecycle.lifecycle.addObserver(graph.sessionLockManager)
        setContent { DesktopApp(graph, AppWindow.None) }
        runOnIdle { graph.sessionLockManager.lockNow() }

        press(Res.string.lock_use_recovery_phrase)
        val cell = runBlocking { getString(Res.string.recovery_word_cell, 1, 12) }
        waitUntil(timeoutMillis = LONG_WAIT_MS) {
            onAllNodesWithContentDescription(cell).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithContentDescription(cell)[0].performTextReplacement(words)
        press(Res.string.lock_recover_access)
        waitForText(Res.string.recovery_choice_body_passphrase)
        waitForText(Res.string.recovery_set_new_passphrase)
        // Navigation has to be asked from the UI thread, as a real click would.
        runOnUiThread {
            onNode(hasText(text(Res.string.recovery_set_new_passphrase)) and hasClickAction()).performClick()
        }
        waitForText(Res.string.passphrase_create_title)
        field().performTextInput("a brand new passphrase")
        press(Res.string.action_continue)
        waitForText(Res.string.passphrase_change_confirm_title)
        field().performTextInput("a brand new passphrase")
        press(Res.string.action_continue)

        waitForText(Res.string.notes_title)
        assertTrue(security.verifyPin("a brand new passphrase".toCharArray()))
    }

    private companion object {
        const val LONG_WAIT_MS = 60_000L
    }
}
