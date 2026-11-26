package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pasich.encly.core.common.asString
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.onboarding_pin_again_body
import com.pasich.encly.core.resources.onboarding_pin_again_title
import com.pasich.encly.core.resources.onboarding_pin_body
import com.pasich.encly.core.resources.onboarding_pin_title
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.platform.secretString
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.screen.pincode.PassphraseField
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.presentation.screen.pincode.PassphraseSuggestion
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel.OnboardingUiState
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Step 1 where the vault's secret is a passphrase: choose it, then type it again. The twin of the PIN keypad step:
 * the same heading, a masked field in place of the dots and keys, "Suggest a passphrase" on the first entry, and the
 * reason a passphrase was refused (or that the two did not match) under the field. The button is the footer's, and
 * Enter sends it too. What is typed lives in [input], which the screen empties when it is sent.
 */
@Composable
internal fun PassphraseSetupStep(
    state: OnboardingUiState,
    input: PassphraseInput,
    actions: PinActions,
    modifier: Modifier = Modifier,
) {
    val kind = state.secretKind
    val confirming = state.isConfirmingPin
    Column(
        modifier = modifier
            .padding(StepPadding)
            .padding(top = EnclyTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.stepGap),
    ) {
        StepHeading(
            title = secretString(
                kind,
                if (confirming) Res.string.onboarding_pin_again_title else Res.string.onboarding_pin_title,
            ),
            body = secretString(
                kind,
                if (confirming) Res.string.onboarding_pin_again_body else Res.string.onboarding_pin_body,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            PassphraseField(
                input = input,
                label = stringResource(Res.string.passphrase_label),
                onSubmit = actions.onSubmit,
                enabled = !state.isLoading,
                onEdit = actions.onPassphraseEdited,
            )
            PinError(state.pinError?.asString(), Modifier.align(Alignment.Start))
            // Only where it is chosen; the confirming entry is typed, not offered.
            if (!confirming) PassphraseSuggestion(input, actions.onSuggest, enabled = !state.isLoading)
        }
    }
}
