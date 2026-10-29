package com.pasich.encly.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.handoff_error_vault
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.platform.CacheDirectory
import com.pasich.encly.presentation.screen.handoff.ImportFromMyNotesScreen
import com.pasich.encly.presentation.viewmodel.ImportFromMyNotesViewModel
import com.pasich.encly.testutil.FakeDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path
import org.junit.Test

/** The hand-off screen while the vault is closed. */
class ImportFromMyNotesScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    @Test
    fun aVaultNothingCanOpenIsReportedInsteadOfTheLockScreen() {
        val handoff = ImportFromMyNotesViewModel(
            documents = FakeDocuments(),
            cache = object : CacheDirectory {
                override val path = Path(context.cacheDir.path)
            },
            securityManager = app.security,
            sessionLockManager = app.sessionLock,
            backupManager = app.backupManager,
            ioDispatcher = Dispatchers.Main,
            savedStateHandle = SavedStateHandle(),
        )
        app.sessionLock.requireUnlock()
        setScreen(viewModels(handoff, app.lock())) { ImportFromMyNotesScreen(onClose = {}) }
        waitForText(str(Res.string.lock_title))

        // The lock screen found that the PIN key is gone and there is nothing else to unlock with.
        rule.runOnIdle { handoff.onVaultLost() }

        waitForText(str(Res.string.handoff_error_vault))
    }
}
