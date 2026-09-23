package com.pasich.encly.presentation.screen.backup

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.BackupStep

/** The dialog for the current [step] of a backup or vault flow, if it has one. */
@Composable
fun BackupDialogs(actions: BackupDialogActions, step: BackupStep) {
    when (step) {
        is BackupStep.Reauth -> ReauthDialog(actions, step)

        BackupStep.NeedsPhrase -> MessageDialog(
            title = R.string.backup_needs_phrase_title,
            body = R.string.backup_needs_phrase_body,
            confirm = R.string.backup_create_phrase,
            onConfirm = actions.phrase::create,
            onDismiss = actions.cancel,
        )

        is BackupStep.ShowNewPhrase -> NewPhraseDialog(step.words, actions)

        is BackupStep.CheckNewPhrase -> PhraseCheckDialog(step, actions)

        is BackupStep.ConfirmExistingPhrase -> PhraseEntryDialog(
            texts = PhraseEntryTexts(R.string.backup_confirm_phrase_title, R.string.backup_confirm_phrase_body),
            error = step.error,
            onSubmit = actions.phrase::submitExisting,
            onDismiss = actions.cancel,
        )

        is BackupStep.EnterImportPhrase -> PhraseEntryDialog(
            texts = PhraseEntryTexts(
                R.string.backup_enter_phrase_title,
                R.string.backup_enter_phrase_body,
                R.string.backup_decrypt,
            ),
            error = step.error,
            onSubmit = actions.importing::submitPhrase,
            onDismiss = actions.cancel,
        )

        is BackupStep.ChooseImportMode -> ImportModeDialog(step, actions)

        BackupStep.ConfirmReplace -> MessageDialog(
            title = R.string.backup_replace_confirm_title,
            body = R.string.backup_replace_confirm_body,
            confirm = R.string.backup_replace,
            onConfirm = actions.importing::confirmReplace,
            onDismiss = actions.cancel,
        )

        BackupStep.ConfirmErase -> MessageDialog(
            title = R.string.security_erase_confirm_title,
            body = R.string.security_erase_confirm_body,
            confirm = R.string.security_erase_confirm,
            onConfirm = actions.eraseAllData,
            onDismiss = actions.cancel,
        )

        else -> Unit
    }
}

@Composable
private fun MessageDialog(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Full-screen PIN entry (plus biometric, when enabled) before an export or import. */
@Composable
private fun ReauthDialog(actions: BackupDialogActions, step: BackupStep.Reauth) {
    val activity = LocalActivity.current as? FragmentActivity
    var input by remember { mutableStateOf("") }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    // Keyed on the step too: a wrong PIN (a new step) may just have started a lockout.
    PinLockoutTicker(step to (lockoutSeconds > 0L), actions.reauth::pinLockoutRemainingMillis) {
        lockoutSeconds = it
    }
    Dialog(
        onDismissRequest = actions.cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PinEntryScaffold(
                title = stringResource(R.string.backup_reauth_title),
                subtitle = if (lockoutSeconds > 0L) {
                    pinLockoutText(lockoutSeconds)
                } else {
                    stringResource(R.string.backup_reauth_subtitle)
                },
                subtitleIsError = lockoutSeconds > 0L,
                error = step.error?.let { stringResource(it) },
            ) {
                PinCodeWidget(
                    pinInput = input,
                    onPinChange = {
                        if (input.length < PIN_LENGTH && lockoutSeconds <= 0L) input += it
                        if (input.length == PIN_LENGTH) {
                            actions.reauth.submitPin(input)
                            input = ""
                        }
                    },
                    onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) },
                )
                if (step.biometric && activity != null) {
                    TextButton(onClick = { actions.reauth.withBiometric(activity) }) {
                        Text(stringResource(R.string.lock_use_biometric))
                    }
                }
                TextButton(onClick = actions.cancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun NewPhraseDialog(words: List<String>, actions: BackupDialogActions) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_needs_phrase_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.backup_phrase_write_down))
                words.forEachIndexed { index, word ->
                    Text(
                        text = stringResource(R.string.backup_phrase_word, index + 1, word),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.phrase::writtenDown) {
                Text(stringResource(R.string.backup_phrase_written))
            }
        },
        dismissButton = { TextButton(onClick = actions.cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PhraseCheckDialog(step: BackupStep.CheckNewPhrase, actions: BackupDialogActions) {
    val answers = remember(step.positions) { List(step.positions.size) { "" }.toMutableStateList() }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_phrase_check_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                step.positions.forEachIndexed { i, position ->
                    OutlinedTextField(
                        value = answers[i],
                        onValueChange = { answers[i] = it },
                        label = { Text(stringResource(R.string.backup_phrase_check_word, position + 1)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
                step.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.phrase.submitCheck(answers.toList()) }) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = actions.cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class PhraseEntryTexts(val title: Int, val body: Int, val confirm: Int = R.string.action_continue)

@Composable
private fun PhraseEntryDialog(
    texts: PhraseEntryTexts,
    error: Int?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(texts.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(texts.body))
                RecoveryPhraseField(value = phrase, onValueChange = { phrase = it })
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(phrase) }, enabled = phrase.isNotBlank()) {
                Text(stringResource(texts.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ImportModeDialog(step: BackupStep.ChooseImportMode, actions: BackupDialogActions) {
    AlertDialog(
        onDismissRequest = actions.cancel,
        title = { Text(stringResource(R.string.backup_import_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_import_contents, step.notes, step.tasks, step.tags))
                Text(stringResource(R.string.backup_merge_desc), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.backup_replace_desc), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.importing::merge) { Text(stringResource(R.string.backup_merge)) }
        },
        dismissButton = {
            TextButton(onClick = actions.importing::askReplace) {
                Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
