package com.pasich.encly.presentation.screen.backup

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.backup_confirm_phrase_body
import com.pasich.encly.core.resources.backup_confirm_phrase_title
import com.pasich.encly.core.resources.backup_count_notes
import com.pasich.encly.core.resources.backup_count_tags
import com.pasich.encly.core.resources.backup_count_tasks
import com.pasich.encly.core.resources.backup_create_phrase
import com.pasich.encly.core.resources.backup_decrypt
import com.pasich.encly.core.resources.backup_enter_phrase_body
import com.pasich.encly.core.resources.backup_enter_phrase_title
import com.pasich.encly.core.resources.backup_import_choose
import com.pasich.encly.core.resources.backup_import_mode_title
import com.pasich.encly.core.resources.backup_import_summary
import com.pasich.encly.core.resources.backup_merge
import com.pasich.encly.core.resources.backup_merge_row_desc
import com.pasich.encly.core.resources.backup_needs_phrase_body
import com.pasich.encly.core.resources.backup_needs_phrase_title
import com.pasich.encly.core.resources.backup_phrase_write_down
import com.pasich.encly.core.resources.backup_phrase_written
import com.pasich.encly.core.resources.backup_reauth_subtitle
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.backup_replace
import com.pasich.encly.core.resources.backup_replace_all
import com.pasich.encly.core.resources.backup_replace_confirm_body
import com.pasich.encly.core.resources.backup_replace_confirm_title
import com.pasich.encly.core.resources.backup_replace_row_desc
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.onboarding_phrase_hide
import com.pasich.encly.core.resources.onboarding_phrase_show
import com.pasich.encly.core.resources.onboarding_phrase_title
import com.pasich.encly.core.resources.onboarding_verify_body
import com.pasich.encly.core.resources.onboarding_verify_confirm
import com.pasich.encly.core.resources.onboarding_verify_show_again
import com.pasich.encly.core.resources.onboarding_verify_title
import com.pasich.encly.core.resources.recovery_phrase_entry_label
import com.pasich.encly.core.resources.security_recovery_replace_confirm
import com.pasich.encly.core.resources.security_recovery_replace_confirm_body
import com.pasich.encly.core.resources.security_recovery_replace_confirm_title
import com.pasich.encly.core.resources.security_recovery_replace_title
import com.pasich.encly.core.resources.security_recovery_replace_warning_body
import com.pasich.encly.core.resources.security_recovery_replace_warning_title
import com.pasich.encly.platform.LocalBiometricHost
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
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.ReauthFlow
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The dialog or full-screen step for the current [step] of a backup or vault flow, if it has
 * one. Phrase steps and the import choice cover the whole screen like the onboarding steps, so
 * callers draw this last, over their content (in a Box). [busy] shows progress on the primary.
 */
@Composable
fun BackupDialogs(actions: BackupDialogActions, step: BackupStep, busy: Boolean = false) {
    when (step) {
        is BackupStep.Reauth -> ReauthDialog(actions.reauth, step, actions.cancel)

        BackupStep.NeedsPhrase -> MessageDialog(
            title = Res.string.backup_needs_phrase_title,
            body = Res.string.backup_needs_phrase_body,
            confirm = Res.string.backup_create_phrase,
            onConfirm = actions.phrase::create,
            onDismiss = actions.cancel,
        )

        is BackupStep.ShowNewPhrase -> NewPhraseStep(step.words, step.replacing, actions)

        BackupStep.ConfirmReplacePhrase -> ReplacePhraseDialog(actions)

        is BackupStep.CheckNewPhrase -> PhraseCheckStep(step, actions, busy)

        is BackupStep.ConfirmExistingPhrase -> PhraseEntryStep(
            texts = PhraseEntryTexts(
                Res.string.backup_needs_phrase_title,
                Res.string.backup_confirm_phrase_title,
                Res.string.backup_confirm_phrase_body,
            ),
            error = step.error,
            busy = busy,
            actions = actions,
            onSubmit = actions.phrase::submitExisting,
        )

        is BackupStep.EnterImportPhrase -> PhraseEntryStep(
            texts = PhraseEntryTexts(
                Res.string.backup_import_choose,
                Res.string.backup_enter_phrase_title,
                Res.string.backup_enter_phrase_body,
                Res.string.backup_decrypt,
            ),
            error = step.error,
            busy = busy,
            actions = actions,
            onSubmit = actions.importing::submitPhrase,
        )

        is BackupStep.ChooseImportMode -> ImportModeStep(step, actions, busy)

        BackupStep.ConfirmReplace -> MessageDialog(
            title = Res.string.backup_replace_confirm_title,
            body = Res.string.backup_replace_confirm_body,
            confirm = Res.string.backup_replace,
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
    title = Res.string.security_recovery_replace_confirm_title,
    body = Res.string.security_recovery_replace_confirm_body,
    confirm = Res.string.security_recovery_replace_confirm,
    onConfirm = actions.phrase::replace,
    onDismiss = actions.cancel,
)

@Composable
private fun MessageDialog(
    title: StringResource,
    body: StringResource,
    confirm: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    EnclyDialog(
        title = stringResource(title),
        text = stringResource(body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(confirm), onConfirm, destructive = destructive),
        dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
    )
}

/**
 * Full-screen PIN entry (plus the fingerprint key, when enabled) before an export or import, run
 * by [reauth]; [onCancel] closes it. The one re-authentication every export goes through.
 */
@Composable
internal fun ReauthDialog(reauth: ReauthFlow, step: BackupStep.Reauth, onCancel: () -> Unit) {
    val host = LocalBiometricHost.current
    // Wiped once submitted or when the dialog closes, never a String (see PinBuffer).
    val input = remember { PinBuffer() }
    DisposableEffect(input) { onDispose { input.clear() } }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    // Keyed on the step too: a wrong PIN (a new step) may just have started a lockout.
    PinLockoutTicker(step to (lockoutSeconds > 0L), reauth::pinLockoutRemainingMillis) {
        lockoutSeconds = it
    }
    val lockedOut = lockoutSeconds > 0L
    val error = step.error?.let { stringResource(it) }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PinEntryScaffold(
            title = stringResource(Res.string.backup_reauth_title),
            subtitle = when {
                lockedOut -> pinLockoutText(lockoutSeconds)
                error != null -> error
                else -> stringResource(Res.string.backup_reauth_subtitle)
            },
            subtitleIsError = lockedOut || error != null,
            bodyAtBottom = true,
            modifier = Modifier.fillMaxSize(),
        ) {
            PinEntry(
                entered = input.length,
                error = error != null && input.length == 0,
                shakeKey = step.failures,
                enabled = !lockedOut,
                actions = PinEntryActions(
                    onDigit = { digit ->
                        if (!lockedOut) input.add(digit)
                        // The flow wipes the PIN it is given.
                        if (input.isFull) reauth.submitPin(input.take())
                    },
                    onBackspace = input::deleteLast,
                    onBiometric = if (step.biometric && host != null) {
                        { reauth.withBiometric(host) }
                    } else {
                        null
                    },
                ),
            )
            EnclyTextButton(
                text = stringResource(Res.string.cancel),
                onClick = onCancel,
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
            if (replacing) Res.string.security_recovery_replace_title else Res.string.backup_needs_phrase_title,
        ),
        onBack = actions.cancel,
        footer = { OnboardingFooter(FooterSpec(Res.string.backup_phrase_written, actions.phrase::writtenDown)) },
    ) {
        StepHeading(
            title = stringResource(Res.string.onboarding_phrase_title),
            body = stringResource(Res.string.backup_phrase_write_down),
        )
        if (replacing) {
            EnclyCallout(
                title = stringResource(Res.string.security_recovery_replace_warning_title),
                text = stringResource(Res.string.security_recovery_replace_warning_body),
                tone = CalloutTone.WARNING,
            )
        }
        WordGrid(words = words, hidden = hidden)
        EnclyPillButton(
            text = stringResource(if (hidden) Res.string.onboarding_phrase_show else Res.string.onboarding_phrase_hide),
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
        label = stringResource(Res.string.backup_needs_phrase_title),
        onBack = actions.phrase::showAgain,
        footer = {
            OnboardingFooter(
                FooterSpec(
                    primary = Res.string.onboarding_verify_confirm,
                    onPrimary = { actions.phrase.submitCheck(step.positions.map { answers[it].orEmpty() }) },
                    enabled = allChecksCorrect(checks, answers),
                    loading = busy,
                    secondary = Res.string.onboarding_verify_show_again,
                    onSecondary = actions.phrase::showAgain,
                ),
            )
        },
    ) {
        StepHeading(
            title = stringResource(Res.string.onboarding_verify_title),
            body = stringResource(Res.string.onboarding_verify_body),
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
    val label: StringResource,
    val title: StringResource,
    val body: StringResource,
    val confirm: StringResource = Res.string.action_continue,
)

/** The 12 words, one numbered cell each; the primary waits for a valid phrase. */
@Composable
private fun PhraseEntryStep(
    texts: PhraseEntryTexts,
    error: StringResource?,
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
                text = stringResource(Res.string.recovery_phrase_entry_label),
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
        Res.string.backup_import_summary,
        pluralStringResource(Res.plurals.backup_count_notes, step.notes, step.notes),
        pluralStringResource(Res.plurals.backup_count_tasks, step.tasks, step.tasks),
        pluralStringResource(Res.plurals.backup_count_tags, step.tags, step.tags),
    )
    FullScreenStep(
        label = stringResource(Res.string.backup_import_choose),
        onBack = actions.cancel,
        footer = {
            EnclyTextButton(
                text = stringResource(Res.string.cancel),
                onClick = actions.cancel,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EnclyTheme.spacing.gutter)
                    .padding(top = EnclyTheme.spacing.s, bottom = EnclyTheme.spacing.stepGap),
            )
        },
    ) {
        StepHeading(title = stringResource(Res.string.backup_import_mode_title), body = summary)
        EnclyGroup {
            EnclySheetRow(
                title = stringResource(Res.string.backup_merge),
                supporting = stringResource(Res.string.backup_merge_row_desc),
                icon = EnclyIcons.Plus,
                enabled = !busy,
                onClick = actions.importing::merge,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
            EnclyGroupDivider()
            EnclySheetRow(
                title = stringResource(Res.string.backup_replace_all),
                supporting = stringResource(Res.string.backup_replace_row_desc),
                icon = EnclyIcons.Restore,
                destructive = true,
                enabled = !busy,
                onClick = actions.importing::askReplace,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
        }
    }
}
