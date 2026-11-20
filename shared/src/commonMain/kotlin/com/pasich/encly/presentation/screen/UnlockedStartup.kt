package com.pasich.encly.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.platform.LocalHostPlatform
import com.pasich.encly.presentation.dialogs.WhatsNewSheet
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.presentation.viewmodel.StartupViewModel
import com.pasich.encly.utils.currentLanguageTag
import com.pasich.encly.whatsnew.Changelogs
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * What the home screen starts when the vault is open: the trash is emptied of notes older than 28 days, and, the
 * first time after an update, the "What's new" sheet is shown. The sheet waits for the unlock animation to end
 * and is never drawn while the vault is locked, so it cannot appear over the lock screen.
 */
@Composable
internal fun UnlockedStartup(viewModel: StartupViewModel = metroViewModel()) {
    val languageTag = currentLanguageTag()
    val bundled = Changelogs.bundledFor(LocalHostPlatform.current)
    LaunchedEffect(viewModel) { viewModel.onUnlocked(languageTag, bundled) }
    val due by viewModel.whatsNewDue.collectAsStateWithLifecycle()
    val revealing = LocalUnlockReveal.current?.running == true
    val locked = LocalVaultLocked.current
    val notes = due
    if (notes != null && !revealing && !locked) {
        WhatsNewSheet(notes = notes, onDismiss = viewModel::dismissWhatsNew)
    }
}
