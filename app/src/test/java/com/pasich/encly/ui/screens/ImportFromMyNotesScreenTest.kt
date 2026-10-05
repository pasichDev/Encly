package com.pasich.encly.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.handoff.ImportFromMyNotesScreen
import com.pasich.encly.presentation.viewmodel.ImportFromMyNotesViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Test

/** The hand-off screen while the vault is closed. */
class ImportFromMyNotesScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }

    @Test
    fun aVaultNothingCanOpenIsReportedInsteadOfTheLockScreen() {
        val handoff = ImportFromMyNotesViewModel(
            context = context,
            securityManager = app.security,
            sessionLockManager = app.sessionLock,
            backupManager = app.backupManager,
            ioDispatcher = Dispatchers.Main,
            savedStateHandle = SavedStateHandle(),
        )
        app.sessionLock.requireUnlock()
        setScreen(viewModels(handoff, app.lock())) { ImportFromMyNotesScreen(onClose = {}) }
        waitForText(str(R.string.lock_title))

        // The lock screen found that the PIN key is gone and there is nothing else to unlock with.
        rule.runOnIdle { handoff.onVaultLost() }

        waitForText(str(R.string.handoff_error_vault))
    }
}
