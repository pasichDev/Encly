package com.pasich.encly.presentation.screen.backup

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.PhraseInput
import com.pasich.encly.presentation.designsystem.WordGrid
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.ui.theme.EnclyTheme

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
            destructive = true,
            onDismiss = actions.cancel,
        )

        BackupStep.ConfirmErase -> MessageDialog(
            title = R.string.security_erase_confirm_title,
            body = R.string.security_erase_confirm_body,
            confirm = R.string.security_erase_confirm,
            onConfirm = actions.eraseAllData,
            destructive = true,
            onDismiss = actions.cancel,
        )

        else -> Unit
    }
}

@Composable
private fun MessageDialog(
    title: Int,
    body: Int,
    confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    EnclyDialog(
        title = stringResource(title),
        text = stringResource(body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(confirm), onConfirm, destructive = destructive),
        dismiss = DialogAction(stringResource(R.string.cancel), onDismiss),
    )
}

/** Full-screen PIN entry (plus the fingerprint key, when enabled) before an export or import. */
@Composable
private fun ReauthDialog(actions: BackupDialogActions, step: BackupStep.Reauth) {
    val activity = LocalActivity.current as? FragmentActivity
    var input by remember { mutableStateOf("") }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    // Keyed on the step too: a wrong PIN (a new step) may just have started a lockout.
    PinLockoutTicker(step to (lockoutSeconds > 0L), actions.reauth::pinLockoutRemainingMillis) {
        lockoutSeconds = it
    }
    val error = step.error?.let { stringResource(it) }
    Dialog(
        onDismissRequest = actions.cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PinEntryScaffold(
            title = stringResource(R.string.backup_reauth_title),
            subtitle = when {
                lockoutSeconds > 0L -> pinLockoutText(lockoutSeconds)
                error != null -> error
                else -> stringResource(R.string.backup_reauth_subtitle)
            },
            subtitleIsError = lockoutSeconds > 0L || error != null,
            modifier = Modifier.fillMaxSize(),
        ) {
            PinEntry(
                entered = input.length,
                error = error != null && input.isEmpty(),
                actions = PinEntryActions(
                    onDigit = { digit ->
                        if (input.length < PIN_LENGTH && lockoutSeconds <= 0L) input += digit
                        if (input.length == PIN_LENGTH) {
                            actions.reauth.submitPin(input)
                            input = ""
                        }
                    },
                    onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) },
                    onBiometric = if (step.biometric && activity != null) {
                        { actions.reauth.withBiometric(activity) }
                    } else {
                        null
                    },
                ),
            )
            EnclyTextButton(
                text = stringResource(R.string.cancel),
                onClick = actions.cancel,
                modifier = Modifier.padding(top = EnclyTheme.spacing.l),
            )
        }
    }
}

@Composable
private fun NewPhraseDialog(words: List<String>, actions: BackupDialogActions) {
    EnclyDialog(
        title = stringResource(R.string.backup_needs_phrase_title),
        text = stringResource(R.string.backup_phrase_write_down),
        onDismissRequest = {},
        confirm = DialogAction(stringResource(R.string.backup_phrase_written), actions.phrase::writtenDown),
        dismiss = DialogAction(stringResource(R.string.cancel), actions.cancel),
    ) {
        WordGrid(
            words = words,
            hidden = false,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun PhraseCheckDialog(step: BackupStep.CheckNewPhrase, actions: BackupDialogActions) {
    val answers = remember(step.positions) { List(step.positions.size) { "" }.toMutableStateList() }
    EnclyDialog(
        title = stringResource(R.string.backup_phrase_check_title),
        onDismissRequest = {},
        confirm = DialogAction(stringResource(R.string.action_continue), {
            actions.phrase.submitCheck(answers.toList())
        }),
        dismiss = DialogAction(stringResource(R.string.cancel), actions.cancel),
    ) {
        step.positions.forEachIndexed { i, position ->
            EnclyTextField(
                value = answers[i],
                onValueChange = { answers[i] = it },
                label = stringResource(R.string.backup_phrase_check_word, position + 1),
                textStyle = EnclyTheme.typography.dataLarge,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }
        step.error?.let { DialogError(stringResource(it)) }
    }
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
    EnclyDialog(
        title = stringResource(texts.title),
        text = stringResource(texts.body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(texts.confirm), { onSubmit(phrase) }, enabled = phrase.isNotBlank()),
        dismiss = DialogAction(stringResource(R.string.cancel), onDismiss),
    ) {
        PhraseInput(
            value = phrase,
            onValueChange = { phrase = it },
            placeholder = stringResource(R.string.recovery_phrase_label),
            enabled = true,
            error = error != null,
        )
        error?.let { DialogError(stringResource(it)) }
    }
}

@Composable
private fun ImportModeDialog(step: BackupStep.ChooseImportMode, actions: BackupDialogActions) {
    EnclyDialog(
        title = stringResource(R.string.backup_import_choose),
        text = stringResource(R.string.backup_import_contents, step.notes, step.tasks, step.tags),
        onDismissRequest = actions.cancel,
        confirm = DialogAction(stringResource(R.string.backup_merge), actions.importing::merge),
        dismiss = DialogAction(
            stringResource(R.string.backup_replace),
            actions.importing::askReplace,
            destructive = true,
        ),
    ) {
        Text(stringResource(R.string.backup_merge_desc), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.backup_replace_desc), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DialogError(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
