package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.onboarding_get_started
import com.pasich.encly.core.resources.onboarding_have_backup
import com.pasich.encly.core.resources.onboarding_phrase_done
import com.pasich.encly.core.resources.onboarding_ready_open
import com.pasich.encly.core.resources.onboarding_recovery_create
import com.pasich.encly.core.resources.onboarding_recovery_skip
import com.pasich.encly.core.resources.onboarding_verify_confirm
import com.pasich.encly.core.resources.onboarding_verify_show_again
import com.pasich.encly.core.resources.restore
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.viewmodel.OnboardingStep
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel.OnboardingUiState
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

internal fun footerSpec(
    state: OnboardingUiState,
    actions: OnboardingActions,
    restorePhrase: RecoveryPhraseState,
    finishing: Boolean,
): FooterSpec {
    val busy = state.isLoading
    return when (state.step) {
        OnboardingStep.WELCOME -> FooterSpec(
            Res.string.onboarding_get_started,
            actions.onGetStarted,
            secondary = Res.string.onboarding_have_backup,
            onSecondary = actions.onStartRestore,
        )

        OnboardingStep.PIN ->
            FooterSpec(Res.string.action_continue, actions.pin.onSubmit, state.pinLength == PIN_LENGTH, busy)

        OnboardingStep.RECOVERY_INFO -> FooterSpec(
            Res.string.onboarding_recovery_create,
            actions.onCreatePhrase,
            loading = busy,
            secondary = Res.string.onboarding_recovery_skip,
            onSecondary = actions.onSkipPhrase,
            secondaryMuted = true,
        )

        OnboardingStep.PHRASE ->
            FooterSpec(Res.string.onboarding_phrase_done, actions.onWroteWords, state.words.isNotEmpty())

        OnboardingStep.VERIFY -> FooterSpec(
            Res.string.onboarding_verify_confirm,
            actions.onConfirmWords,
            state.isVerificationComplete,
            busy,
            secondary = Res.string.onboarding_verify_show_again,
            onSecondary = actions.onShowWordsAgain,
        )

        OnboardingStep.READY ->
            FooterSpec(Res.string.onboarding_ready_open, actions.onOpenNotebook, !state.biometricPending, finishing)

        OnboardingStep.RESTORE -> FooterSpec(
            Res.string.restore,
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
