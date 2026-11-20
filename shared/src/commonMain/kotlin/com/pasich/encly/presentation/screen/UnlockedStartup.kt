package com.pasich.encly.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.trash_notice_message
import com.pasich.encly.core.resources.trash_notice_open
import com.pasich.encly.core.resources.trash_notice_title
import com.pasich.encly.core.resources.whats_new_dismiss
import com.pasich.encly.platform.LocalHostPlatform
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.dialogs.WhatsNewSheet
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.StartupViewModel
import com.pasich.encly.utils.currentLanguageTag
import com.pasich.encly.whatsnew.Changelogs
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the home screen starts when the vault is open: the trash is emptied of notes older than 28 days, and, the
 * first time after an update, the "What's new" sheet is shown. The sheet waits for the unlock animation to end
 * and is never drawn while the vault is locked, so it cannot appear over the lock screen. After an update from a
 * version below 3.0.0 with notes in the trash, a notice says the trash now empties itself, with a button to open it;
 * it waits for the sheet to be closed.
 */
@Composable
internal fun UnlockedStartup(navController: NavHostController, viewModel: StartupViewModel = metroViewModel()) {
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
    val trashCount by viewModel.trashNoticeCount.collectAsStateWithLifecycle()
    val inTrash = trashCount
    val free = notes == null && !revealing && !locked
    if (inTrash != null && free) TrashNotice(inTrash, viewModel::dismissTrashNotice, navController)
}

/** Says that the trash now empties itself, with [count] notes in it; its button opens the trash. */
@Composable
private fun TrashNotice(count: Int, onDismiss: () -> Unit, navController: NavHostController) {
    EnclyDialog(
        title = stringResource(Res.string.trash_notice_title),
        text = pluralStringResource(Res.plurals.trash_notice_message, count, count),
        onDismissRequest = onDismiss,
        confirm = DialogAction(
            stringResource(Res.string.trash_notice_open),
            onClick = {
                onDismiss()
                val options = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true).build()
                navController.navigate(NavRoutes.TrashRoute.name, options)
            },
        ),
        dismiss = DialogAction(stringResource(Res.string.whats_new_dismiss), onDismiss),
    )
}
