package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.viewmodel.OnboardingStep
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel.OnboardingUiState
import com.pasich.encly.ui.theme.EnclyTheme

internal fun footerSpec(
    state: OnboardingUiState,
    actions: OnboardingActions,
    restorePhrase: RecoveryPhraseState,
    finishing: Boolean,
): FooterSpec {
    val busy = state.isLoading
    return when (state.step) {
        OnboardingStep.WELCOME -> FooterSpec(
            R.string.onboarding_get_started,
            actions.onGetStarted,
            secondary = R.string.onboarding_have_backup,
            onSecondary = actions.onStartRestore,
        )

        OnboardingStep.PIN ->
            FooterSpec(R.string.action_continue, actions.pin.onSubmit, state.pinLength == PIN_LENGTH, busy)

        OnboardingStep.RECOVERY_INFO -> FooterSpec(
            R.string.onboarding_recovery_create,
            actions.onCreatePhrase,
            loading = busy,
            secondary = R.string.onboarding_recovery_skip,
            onSecondary = actions.onSkipPhrase,
            secondaryMuted = true,
        )

        OnboardingStep.PHRASE ->
            FooterSpec(R.string.onboarding_phrase_done, actions.onWroteWords, state.words.isNotEmpty())

        OnboardingStep.VERIFY -> FooterSpec(
            R.string.onboarding_verify_confirm,
            actions.onConfirmWords,
            state.isVerificationComplete,
            busy,
            secondary = R.string.onboarding_verify_show_again,
            onSecondary = actions.onShowWordsAgain,
        )

        OnboardingStep.READY ->
            FooterSpec(R.string.onboarding_ready_open, actions.onOpenNotebook, !state.biometricPending, finishing)

        OnboardingStep.RESTORE -> FooterSpec(
            R.string.restore,
            actions.onRestore,
            state.restoreFileReady && restorePhrase.canSubmit,
            busy,
        )
    }
}

/**
 * The footer: the primary action, and below it a text action or the same space left empty,
 * so the primary button never moves between steps. Text actions wait while the primary runs.
 */
@Composable
internal fun OnboardingFooter(spec: FooterSpec, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = EnclyTheme.spacing.gutter,
                end = EnclyTheme.spacing.gutter,
                top = EnclyTheme.spacing.s,
                bottom = EnclyTheme.spacing.stepGap,
            ),
    ) {
        EnclyButton(
            text = stringResource(spec.primary),
            onClick = spec.onPrimary,
            enabled = spec.enabled,
            loading = spec.loading,
        )
        val secondary = spec.secondary
        if (secondary == null) {
            Spacer(modifier = Modifier.height(EnclyTheme.spacing.textButtonHeight))
        } else {
            EnclyTextButton(
                text = stringResource(secondary),
                onClick = spec.onSecondary,
                enabled = !spec.loading,
                muted = spec.secondaryMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
