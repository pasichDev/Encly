package com.pasich.encly.presentation.screen.pincode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_suggest
import com.pasich.encly.core.resources.passphrase_suggest_note
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The words of a passphrase step: the title and the sub-heading (an error, or a lockout countdown, when
 * [subtitleIsError]; [subtitleTicks] when it changes every second), the field's label and the button's text.
 */
internal class PassphraseStepText(
    val title: String,
    val subtitle: String,
    val label: String,
    val submit: String,
    val subtitleIsError: Boolean = false,
    val subtitleTicks: Boolean = false,
)

/**
 * What a passphrase step does: [onSubmit] sends the passphrase (Enter and the button), [onEdit] runs after an edit,
 * and a [onSuggest] adds "Suggest a passphrase" under the field (null: no suggestion on this step).
 */
internal class PassphraseStepActions(
    val onSubmit: () -> Unit,
    val onEdit: () -> Unit = {},
    val onSuggest: (() -> Unit)? = null,
)

/** How a passphrase step is laid out and whether it takes input. */
internal class PassphraseStepStyle(
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val compact: Boolean = true,
    val icon: ImageVector? = null,
)

/**
 * A step that asks for a passphrase, in the layout of every PIN screen: the title and sub-heading, the masked field
 * and, pinned above the keyboard, the button that sends it (with [footerExtra] under it). It is the twin of a keypad
 * step: the same scaffold, a field in place of the dots and the keys.
 */
@Composable
internal fun PassphraseStep(
    text: PassphraseStepText,
    input: PassphraseInput,
    actions: PassphraseStepActions,
    modifier: Modifier = Modifier,
    style: PassphraseStepStyle = PassphraseStepStyle(),
    footerExtra: @Composable ColumnScope.() -> Unit = {},
) {
    PinEntryScaffold(
        title = text.title,
        subtitle = text.subtitle,
        subtitleIsError = text.subtitleIsError,
        subtitleTicks = text.subtitleTicks,
        icon = style.icon,
        compact = style.compact,
        busy = style.busy,
        modifier = modifier,
        footer = {
            EnclyButton(
                text = text.submit,
                onClick = actions.onSubmit,
                enabled = style.enabled && input.canSubmit,
            )
            footerExtra()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = EnclyTheme.spacing.gutter)) {
            PassphraseField(
                input = input,
                label = text.label,
                onSubmit = { if (style.enabled) actions.onSubmit() },
                enabled = style.enabled,
                onEdit = actions.onEdit,
                modifier = Modifier.padding(top = EnclyTheme.spacing.l),
            )
            actions.onSuggest?.let { PassphraseSuggestion(input, it, enabled = style.enabled) }
        }
    }
}

/** "Suggest a passphrase", and once it was used a note that the words are not the recovery phrase. */
@Composable
internal fun PassphraseSuggestion(
    input: PassphraseInput,
    onSuggest: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = EnclyTheme.spacing.s)) {
        EnclyTextButton(
            text = stringResource(Res.string.passphrase_suggest),
            onClick = onSuggest,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (input.suggested) {
            EnclyCallout(text = stringResource(Res.string.passphrase_suggest_note))
        }
    }
}
