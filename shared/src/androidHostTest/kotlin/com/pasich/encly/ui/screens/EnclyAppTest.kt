package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.lifecycle.ViewModel
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.empty_notes
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.core.resources.main_drawer_settings
import com.pasich.encly.core.resources.note_add
import com.pasich.encly.core.resources.note_title_placeholder
import com.pasich.encly.core.resources.recovery_choice_title
import com.pasich.encly.core.resources.settings_language
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.presentation.navigation.EnclyApp
import com.pasich.encly.presentation.viewmodel.ThemeViewModel
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import kotlin.reflect.KClass

/**
 * The app root the host draws: where it starts for the vault's state, and the guards that keep the vault's
 * screens away from a closed vault (a re-lock, a stale navigation, a session unlocked elsewhere).
 */
class EnclyAppTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    /** `metroViewModel()` in any destination answers with the ViewModels built from fakes. */
    private class Prebuilt(vararg viewModels: ViewModel) : MetroViewModelFactory() {
        override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> =
            viewModels.associate { it::class to { it } }
    }

    private fun show(status: InitialStatus?, unlocked: Boolean = true, recovery: RecoveryStage = RecoveryStage.NONE) {
        `when`(app.security.isDatabaseUnlocked()).thenReturn(unlocked)
        `when`(app.security.recoveryStage()).thenReturn(recovery)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        val factory =
            Prebuilt(ThemeViewModel(app.settings), app.lock(), app.editor(), app.markdownExport(), *app.home())
        rule.setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
                EnclyApp(status, app.security, app.sessionLock)
            }
        }
        rule.waitForIdle()
    }

    private fun onLockScreen() = countText(str(Res.string.lock_title)) > 0

    private fun onHome() = countText(str(Res.string.empty_notes)) > 0

    private fun onEditor() = countText(str(Res.string.note_title_placeholder)) > 0

    @Test
    fun anOpenVaultStartsOnHome() {
        show(InitialStatus.MAIN)

        waitFor { onHome() }
        assertEquals(0, countText(str(Res.string.lock_title)))
    }

    @Test
    fun anOpenVaultThatStillOwesTheRecoveryChoiceStartsOnTheChoiceNotOnHome() {
        // A second window of the desktop opened after another one unlocked with the recovery phrase.
        show(InitialStatus.MAIN, recovery = RecoveryStage.CHOOSE)

        waitForText(str(Res.string.recovery_choice_title))
        assertEquals(0, countText(str(Res.string.empty_notes)))
    }

    @Test
    fun aKeyboardCommandCannotLeaveTheRecoveryChoice() {
        show(InitialStatus.MAIN, recovery = RecoveryStage.CHOOSE)
        waitForText(str(Res.string.recovery_choice_title))

        // Ctrl+N would start a note, which leaves the screen that must be answered first.
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.N) } }
        rule.waitForIdle()

        assertEquals(0, countText(str(Res.string.note_title_placeholder)))
        assertEquals(1, countText(str(Res.string.recovery_choice_title)))
    }

    @Test
    fun aCommittedVaultThatIsNotOpenStartsOnTheLockScreen() {
        show(InitialStatus.AUTH, unlocked = false)

        waitFor { onLockScreen() }
    }

    @Test
    fun nothingIsDrawnUntilTheVaultStateIsKnown() {
        show(null)

        assertEquals(0, visibleTexts().size)
    }

    @Test
    fun aReLockReplacesTheScreenWithTheLockScreenAndNothingOfTheVaultStaysDrawn() {
        show(InitialStatus.MAIN)
        waitFor { onHome() }

        rule.runOnIdle { app.sessionLock.requireUnlock() }

        waitFor { onLockScreen() }
        assertEquals(0, countText(str(Res.string.empty_notes)))
    }

    @Test
    fun aNavigationToAVaultScreenWhileTheVaultIsClosedIsSentToTheLockScreen() {
        show(InitialStatus.MAIN)
        waitFor { onHome() }

        // Something closed the database without the lock event arriving yet; the next vault screen must not show.
        `when`(app.security.isDatabaseUnlocked()).thenReturn(false)
        rule.onNodeWithContentDescription(str(Res.string.main_drawer_settings)).performClick()

        waitFor { onLockScreen() }
        assertEquals(0, countText(str(Res.string.settings_language)))
    }

    @Test
    fun aLockWhileTheEditorIsOpenReplacesItWithTheLockScreen() {
        show(InitialStatus.MAIN)
        waitFor { onHome() }
        rule.onNodeWithText(str(Res.string.note_add), useUnmergedTree = true).performClick()
        waitFor { onEditor() }

        rule.runOnIdle { app.sessionLock.requireUnlock() }

        waitFor { onLockScreen() }
        assertEquals(0, countText(str(Res.string.note_title_placeholder)))
    }

    @Test
    fun aLockWhileASettingsScreenIsOpenReplacesItWithTheLockScreen() {
        show(InitialStatus.MAIN)
        waitFor { onHome() }
        rule.onNodeWithContentDescription(str(Res.string.main_drawer_settings)).performClick()
        waitForText(str(Res.string.settings_language))

        rule.runOnIdle { app.sessionLock.requireUnlock() }

        waitFor { onLockScreen() }
        assertEquals(0, countText(str(Res.string.settings_language)))
    }
}
