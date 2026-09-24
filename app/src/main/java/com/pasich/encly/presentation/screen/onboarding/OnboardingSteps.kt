package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.DoneRow
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyCard
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyPillButton
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.FactRow
import com.pasich.encly.presentation.designsystem.FilePickerButton
import com.pasich.encly.presentation.designsystem.FormSection
import com.pasich.encly.presentation.designsystem.KeypadSize
import com.pasich.encly.presentation.designsystem.PinDots
import com.pasich.encly.presentation.designsystem.PinKeypad
import com.pasich.encly.presentation.designsystem.RecoveryPhraseInput
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.designsystem.UseRow
import com.pasich.encly.presentation.designsystem.WordGrid
import com.pasich.encly.presentation.viewmodel.OnboardingPath
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel.OnboardingUiState
import com.pasich.encly.presentation.viewmodel.SecurityType
import com.pasich.encly.ui.theme.EnclyTheme

/** Step 0: what Encly is, in three facts. */
@Composable
internal fun WelcomeStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.heroTop),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.heroGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m)) {
            Text(
                text = stringResource(R.string.onboarding_welcome_headline),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.onboarding_welcome_sub),
                style = EnclyTheme.typography.heroSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.factGap)) {
            FactRow(
                EnclyIcons.UserOff,
                stringResource(R.string.onboarding_fact_account_title),
                stringResource(R.string.onboarding_fact_account_desc),
            )
            FactRow(
                EnclyIcons.CloudOff,
                stringResource(R.string.onboarding_fact_offline_title),
                stringResource(R.string.onboarding_fact_offline_desc),
            )
            FactRow(
                EnclyIcons.Shield,
                stringResource(R.string.onboarding_fact_encrypted_title),
                stringResource(R.string.onboarding_fact_encrypted_desc),
            )
        }
    }
}

/** What the PIN step reports back to the ViewModel. */
internal class PinActions(
    val onDigit: (Int) -> Unit,
    val onBackspace: () -> Unit,
    val onBiometricChange: (Boolean) -> Unit,
    val onSubmit: () -> Unit,
)

/**
 * Step 1: choose the PIN, then type it again; the biometric switch below the keypad. Digits
 * from a hardware keyboard work too.
 */
@Composable
internal fun PinStep(state: OnboardingUiState, actions: PinActions, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        modifier = modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event -> event.type == KeyEventType.KeyDown && handleKey(event.key, actions) },
    ) {
        Column(
            modifier = Modifier
                .padding(StepPadding)
                .padding(top = EnclyTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.stepGap),
        ) {
            StepHeading(
                title = stringResource(
                    if (state.isConfirmingPin) R.string.onboarding_pin_again_title else R.string.onboarding_pin_title,
                ),
                body = stringResource(
                    if (state.isConfirmingPin) R.string.onboarding_pin_again_body else R.string.onboarding_pin_body,
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
                PinDots(entered = state.pinLength, error = state.pinError != null)
                val error = state.pinError?.asString()
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        PinKeypad(
            onDigit = actions.onDigit,
            onBackspace = actions.onBackspace,
            size = KeypadSize.SETUP,
            enabled = !state.isLoading,
            modifier = Modifier.padding(top = EnclyTheme.spacing.stepGap),
        )
        if (state.biometricAvailable) {
            BiometricCallout(
                checked = state.biometricRequested,
                onCheckedChange = actions.onBiometricChange,
                modifier = Modifier
                    .padding(StepPadding)
                    .padding(top = EnclyTheme.spacing.factGap),
            )
        }
    }
}

private fun handleKey(key: Key, actions: PinActions): Boolean {
    val digit = DIGIT_KEYS.indexOf(key)
    return when {
        digit >= 0 -> {
            actions.onDigit(digit)
            true
        }

        key == Key.Backspace -> {
            actions.onBackspace()
            true
        }

        key == Key.Enter || key == Key.NumPadEnter -> {
            actions.onSubmit()
            true
        }

        else -> false
    }
}

private val DIGIT_KEYS = listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)

@Composable
private fun BiometricCallout(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        EnclySwitchRow(
            title = stringResource(R.string.pin_unlock_with_fingerprint),
            supporting = stringResource(R.string.onboarding_biometric_desc),
            icon = EnclyIcons.Fingerprint,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
        )
    }
}

/** Step 2: why a recovery phrase, and what losing it means. */
@Composable
internal fun RecoveryInfoStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.l),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_recovery_title),
            body = stringResource(R.string.onboarding_recovery_body),
        )
        Column {
            UseRow(
                EnclyIcons.Key,
                stringResource(R.string.onboarding_recovery_pin_title),
                stringResource(R.string.onboarding_recovery_pin_desc),
            )
            UseRow(
                EnclyIcons.File,
                stringResource(R.string.onboarding_recovery_backups_title),
                stringResource(R.string.onboarding_recovery_backups_desc),
            )
            UseRow(
                EnclyIcons.Phone,
                stringResource(R.string.onboarding_recovery_phone_title),
                stringResource(R.string.onboarding_recovery_phone_desc),
            )
        }
        EnclyCallout(text = stringResource(R.string.onboarding_recovery_warning), tone = CalloutTone.WARNING)
    }
}

/** Step 3: the twelve words to write down. */
@Composable
internal fun PhraseStep(state: OnboardingUiState, onToggleWords: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.l),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_phrase_title),
            body = stringResource(R.string.onboarding_phrase_body),
        )
        if (state.words.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (state.error == null) CircularProgressIndicator()
            }
        } else {
            WordGrid(words = state.words, hidden = state.wordsHidden)
            EnclyPillButton(
                text = stringResource(
                    if (state.wordsHidden) R.string.onboarding_phrase_show else R.string.onboarding_phrase_hide,
                ),
                leadingIcon = if (state.wordsHidden) EnclyIcons.Eye else EnclyIcons.EyeOff,
                onClick = onToggleWords,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Step 3b: type three of the words back. */
@Composable
internal fun VerifyStep(state: OnboardingUiState, onAnswer: (Int, String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.stepGap),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_verify_title),
            body = stringResource(R.string.onboarding_verify_body),
        )
        PhraseCheckFields(
            checks = state.verificationWords,
            answers = state.userAnswers,
            onAnswer = onAnswer,
            enabled = !state.isLoading,
        )
    }
}

/** Step 4: what was set up. */
@Composable
internal fun ReadyStep(state: OnboardingUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.readyTop),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xl),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_ready_title),
            body = stringResource(R.string.onboarding_ready_body),
            large = true,
        )
        EnclyCard(contentPadding = PaddingValues(EnclyTheme.spacing.factGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.fieldGap)) {
                DoneRow(
                    stringResource(R.string.onboarding_ready_pin_title),
                    stringResource(R.string.onboarding_ready_pin_desc),
                )
                if (state.biometricEnabled) {
                    DoneRow(
                        stringResource(R.string.onboarding_ready_biometric_title),
                        stringResource(R.string.onboarding_ready_biometric_desc),
                    )
                }
                when {
                    state.path == OnboardingPath.RESTORE -> DoneRow(
                        stringResource(R.string.onboarding_ready_restored_title),
                        stringResource(R.string.onboarding_ready_restored_desc),
                    )

                    state.securityType == SecurityType.AUTO_MANAGED -> DoneRow(
                        stringResource(R.string.onboarding_ready_no_phrase_title),
                        stringResource(R.string.onboarding_ready_no_phrase_desc),
                        done = false,
                    )

                    else -> DoneRow(
                        stringResource(R.string.onboarding_ready_phrase_title),
                        stringResource(R.string.onboarding_ready_phrase_desc),
                    )
                }
            }
        }
        if (state.securityType != SecurityType.AUTO_MANAGED) {
            Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap)) {
                Icon(
                    EnclyIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
                )
                Text(
                    text = stringResource(R.string.onboarding_ready_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What the Restore step reports back. */
internal class RestoreActions(val onPickFile: () -> Unit, val onPhraseEdited: () -> Unit, val onSubmit: () -> Unit)

/** Alt: restore from a backup file and the 12 words it was made with, one numbered cell each. */
@Composable
internal fun RestoreStep(
    state: OnboardingUiState,
    phrase: RecoveryPhraseState,
    actions: RestoreActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.l),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_restore_title),
            body = stringResource(R.string.onboarding_restore_intro),
        )
        FormSection(title = stringResource(R.string.onboarding_restore_file_section)) {
            FilePickerButton(
                title = state.restoreFileName.takeIf { state.restoreFileReady }
                    ?: stringResource(R.string.onboarding_restore_pick),
                hint = if (state.restoreFileReady) stringResource(R.string.onboarding_restore_tap_other) else null,
                enabled = !state.isLoading,
                onClick = actions.onPickFile,
            )
            state.restoreFileError?.let { ErrorText(it.asString()) }
        }
        FormSection(title = stringResource(R.string.onboarding_restore_phrase_section)) {
            Text(
                text = stringResource(R.string.recovery_phrase_entry_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = EnclyTheme.spacing.xxs),
            )
            RecoveryPhraseInput(
                state = phrase,
                enabled = !state.isLoading,
                error = state.restorePhraseError?.asString(),
                onEdit = actions.onPhraseEdited,
                onDone = actions.onSubmit,
            )
            Text(
                text = stringResource(R.string.onboarding_restore_phrase_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
