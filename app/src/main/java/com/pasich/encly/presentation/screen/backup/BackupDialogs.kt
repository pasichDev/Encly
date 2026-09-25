package com.pasich.encly.presentation.screen.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyPillButton
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.LabelHeader
import com.pasich.encly.presentation.designsystem.RecoveryPhraseInput
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.designsystem.WordGrid
import com.pasich.encly.presentation.screen.onboarding.FooterSpec
import com.pasich.encly.presentation.screen.onboarding.OnboardingFooter
import com.pasich.encly.presentation.screen.onboarding.PhraseCheckFields
import com.pasich.encly.presentation.screen.onboarding.allChecksCorrect
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * The dialog or full-screen step for the current [step] of a backup or vault flow, if it has
 * one. Phrase steps and the import choice cover the whole screen like the onboarding steps, so
 * callers draw this last, over their content (in a Box). [busy] shows progress on the primary.
 */
@Composable
fun BackupDialogs(actions: BackupDialogActions, step: BackupStep, busy: Boolean = false) {
    when (step) {
        is BackupStep.Reauth -> ReauthDialog(actions, step)

        BackupStep.NeedsPhrase -> MessageDialog(
            title = R.string.backup_needs_phrase_title,
            body = R.string.backup_needs_phrase_body,
            confirm = R.string.backup_create_phrase,
            onConfirm = actions.phrase::create,
            onDismiss = actions.cancel,
        )

        is BackupStep.ShowNewPhrase -> NewPhraseStep(step.words, step.replacing, actions)

        BackupStep.ConfirmReplacePhrase -> ReplacePhraseDialog(actions)

        is BackupStep.CheckNewPhrase -> PhraseCheckStep(step, actions, busy)

        is BackupStep.ConfirmExistingPhrase -> PhraseEntryStep(
            texts = PhraseEntryTexts(
                R.string.backup_needs_phrase_title,
                R.string.backup_confirm_phrase_title,
                R.string.backup_confirm_phrase_body,
            ),
            error = step.error,
            busy = busy,
            actions = actions,
            onSubmit = actions.phrase::submitExisting,
        )

        is BackupStep.EnterImportPhrase -> PhraseEntryStep(
            texts = PhraseEntryTexts(
                R.string.backup_import_choose,
                R.string.backup_enter_phrase_title,
                R.string.backup_enter_phrase_body,
                R.string.backup_decrypt,
            ),
            error = step.error,
            busy = busy,
            actions = actions,
            onSubmit = actions.importing::submitPhrase,
        )

        is BackupStep.ChooseImportMode -> ImportModeStep(step, actions, busy)

        BackupStep.ConfirmReplace -> MessageDialog(
            title = R.string.backup_replace_confirm_title,
            body = R.string.backup_replace_confirm_body,
            confirm = R.string.backup_replace,
            onConfirm = actions.importing::confirmReplace,
            destructive = true,
            // Cancel goes back to the choice: the backup was decrypted with 12 typed words.
            onDismiss = actions.importing::backToChoice,
        )

        else -> Unit
    }
}

/** Before new recovery words: the old ones stop working here, old backups keep needing them. */
@Composable
private fun ReplacePhraseDialog(actions: BackupDialogActions) = MessageDialog(
    title = R.string.security_recovery_replace_confirm_title,
    body = R.string.security_recovery_replace_confirm_body,
    confirm = R.string.security_recovery_replace_confirm,
    onConfirm = actions.phrase::replace,
    onDismiss = actions.cancel,
)

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
    val lockedOut = lockoutSeconds > 0L
    val error = step.error?.let { stringResource(it) }
    Dialog(
        onDismissRequest = actions.cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PinEntryScaffold(
            title = stringResource(R.string.backup_reauth_title),
            subtitle = when {
                lockedOut -> pinLockoutText(lockoutSeconds)
                error != null -> error
                else -> stringResource(R.string.backup_reauth_subtitle)
            },
            subtitleIsError = lockedOut || error != null,
            modifier = Modifier.fillMaxSize(),
        ) {
            PinEntry(
                entered = input.length,
                error = error != null && input.isEmpty(),
                shakeKey = step.failures,
                enabled = !lockedOut,
                actions = PinEntryActions(
                    onDigit = { digit ->
                        if (input.length < PIN_LENGTH && !lockedOut) input += digit
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

/**
 * A backup step laid out like an onboarding step, over the whole screen: a back header with
 * [label], the scrolling [content] in the gutter, and [footer] pinned above the keyboard.
 * System back runs [onBack] too.
 */
@Composable
private fun FullScreenStep(
    label: String,
    onBack: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            LabelHeader(label = label, onBack = onBack)
            Column(
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.l),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = EnclyTheme.spacing.gutter)
                    .padding(top = EnclyTheme.spacing.m, bottom = EnclyTheme.spacing.l),
                content = content,
            )
            footer()
        }
    }
}

/**
 * The new recovery phrase to write down, with a Hide/Show pill, as in onboarding. A
 * [replacing] phrase also says what happens to the old words and the backups made with them.
 */
@Composable
private fun NewPhraseStep(words: List<String>, replacing: Boolean, actions: BackupDialogActions) {
    var hidden by remember { mutableStateOf(false) }
    FullScreenStep(
        label = stringResource(
            if (replacing) R.string.security_recovery_replace_title else R.string.backup_needs_phrase_title,
        ),
        onBack = actions.cancel,
        footer = { OnboardingFooter(FooterSpec(R.string.backup_phrase_written, actions.phrase::writtenDown)) },
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_phrase_title),
            body = stringResource(R.string.backup_phrase_write_down),
        )
        if (replacing) {
            EnclyCallout(
                title = stringResource(R.string.security_recovery_replace_warning_title),
                text = stringResource(R.string.security_recovery_replace_warning_body),
                tone = CalloutTone.WARNING,
            )
        }
        WordGrid(words = words, hidden = hidden)
        EnclyPillButton(
            text = stringResource(if (hidden) R.string.onboarding_phrase_show else R.string.onboarding_phrase_hide),
            leadingIcon = if (hidden) EnclyIcons.Eye else EnclyIcons.EyeOff,
            onClick = { hidden = !hidden },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Three of the new words typed back, each checked as it is typed; Confirm once all are right. */
@Composable
private fun PhraseCheckStep(step: BackupStep.CheckNewPhrase, actions: BackupDialogActions, busy: Boolean) {
    val checks = remember(step.positions, step.expected) { step.positions.zip(step.expected) }
    val answers = remember(step.positions) { mutableStateMapOf<Int, String>() }
    FullScreenStep(
        label = stringResource(R.string.backup_needs_phrase_title),
        onBack = actions.phrase::showAgain,
        footer = {
            OnboardingFooter(
                FooterSpec(
                    primary = R.string.onboarding_verify_confirm,
                    onPrimary = { actions.phrase.submitCheck(step.positions.map { answers[it].orEmpty() }) },
                    enabled = allChecksCorrect(checks, answers),
                    loading = busy,
                    secondary = R.string.onboarding_verify_show_again,
                    onSecondary = actions.phrase::showAgain,
                ),
            )
        },
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_verify_title),
            body = stringResource(R.string.onboarding_verify_body),
        )
        PhraseCheckFields(
            checks = checks,
            answers = answers,
            onAnswer = { index, answer ->
                answers[index] = answer.trim().lowercase()
                actions.clearError()
            },
            enabled = !busy,
        )
    }
}

private data class PhraseEntryTexts(
    val label: Int,
    val title: Int,
    val body: Int,
    val confirm: Int = R.string.action_continue,
)

/** The 12 words, one numbered cell each; the primary waits for a valid phrase. */
@Composable
private fun PhraseEntryStep(
    texts: PhraseEntryTexts,
    error: Int?,
    busy: Boolean,
    actions: BackupDialogActions,
    onSubmit: (CharArray) -> Unit,
) {
    // Never saved state: the words live only while this step does.
    val phrase = remember { RecoveryPhraseState() }
    val submit = { if (phrase.canSubmit && !busy) onSubmit(phrase.toCharArray()) }
    FullScreenStep(
        label = stringResource(texts.label),
        onBack = actions.cancel,
        footer = {
            OnboardingFooter(FooterSpec(texts.confirm, submit, enabled = phrase.canSubmit, loading = busy))
        },
    ) {
        StepHeading(title = stringResource(texts.title), body = stringResource(texts.body))
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
            Text(
                text = stringResource(R.string.recovery_phrase_entry_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RecoveryPhraseInput(
                state = phrase,
                enabled = !busy,
                error = error?.let { stringResource(it) },
                onEdit = actions.clearError,
                onDone = submit,
            )
        }
    }
}

/**
 * What the decrypted backup holds, and the choice: merge, or replace everything (confirmed
 * again). Only Cancel, back and the header drop the decrypted backup.
 */
@Composable
private fun ImportModeStep(step: BackupStep.ChooseImportMode, actions: BackupDialogActions, busy: Boolean) {
    val summary = stringResource(
        R.string.backup_import_summary,
        pluralStringResource(R.plurals.backup_count_notes, step.notes, step.notes),
        pluralStringResource(R.plurals.backup_count_tasks, step.tasks, step.tasks),
        pluralStringResource(R.plurals.backup_count_tags, step.tags, step.tags),
    )
    FullScreenStep(
        label = stringResource(R.string.backup_import_choose),
        onBack = actions.cancel,
        footer = {
            EnclyTextButton(
                text = stringResource(R.string.cancel),
                onClick = actions.cancel,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EnclyTheme.spacing.gutter)
                    .padding(top = EnclyTheme.spacing.s, bottom = EnclyTheme.spacing.stepGap),
            )
        },
    ) {
        StepHeading(title = stringResource(R.string.backup_import_mode_title), body = summary)
        EnclyGroup {
            EnclySheetRow(
                title = stringResource(R.string.backup_merge),
                supporting = stringResource(R.string.backup_merge_row_desc),
                icon = EnclyIcons.Plus,
                enabled = !busy,
                onClick = actions.importing::merge,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
            EnclyGroupDivider()
            EnclySheetRow(
                title = stringResource(R.string.backup_replace_all),
                supporting = stringResource(R.string.backup_replace_row_desc),
                icon = EnclyIcons.Restore,
                destructive = true,
                enabled = !busy,
                onClick = actions.importing::askReplace,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
        }
    }
}
