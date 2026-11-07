package com.pasich.encly.presentation.screen.pincode

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * A step that asks for the password of the vault (to confirm it is the owner): the title and sub-heading of every
 * PIN screen, a masked field and, pinned above the keyboard, the button that sends it. Nothing is sent by itself.
 */
@Composable
internal fun PasswordStepScaffold(
    text: PasswordStepText,
    input: PasswordInput,
    submit: PasswordStepSubmit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onEdit: () -> Unit = {},
    footerExtra: @Composable ColumnScope.() -> Unit = {},
) {
    PinEntryScaffold(
        title = text.title,
        subtitle = text.subtitle,
        subtitleIsError = text.subtitleIsError,
        icon = EnclyIcons.Key,
        compact = true,
        modifier = modifier,
        footer = {
            EnclyButton(text = submit.text, onClick = submit.onClick, enabled = enabled && input.canSubmit)
            footerExtra()
        },
    ) {
        PasswordField(
            input = input,
            label = text.label,
            enabled = enabled,
            onEdit = onEdit,
            onDone = { if (enabled && input.canSubmit) submit.onClick() },
            modifier = Modifier
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .padding(top = EnclyTheme.spacing.l),
        )
    }
}

/**
 * The words of a [PasswordStepScaffold]: its title, the sub-heading (an error or a lockout countdown when
 * [subtitleIsError]) and the field's label.
 */
internal class PasswordStepText(
    val title: String,
    val subtitle: String,
    val label: String,
    val subtitleIsError: Boolean = false,
)

/** The button of a [PasswordStepScaffold]. */
internal class PasswordStepSubmit(val text: String, val onClick: () -> Unit)
