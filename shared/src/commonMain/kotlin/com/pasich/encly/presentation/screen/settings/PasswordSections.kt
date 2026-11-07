package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auth_method_pin_desc
import com.pasich.encly.core.resources.auth_method_pin_none
import com.pasich.encly.core.resources.auth_method_pin_title
import com.pasich.encly.core.resources.password_pin_only_note
import com.pasich.encly.core.resources.password_row_off
import com.pasich.encly.core.resources.password_row_on
import com.pasich.encly.core.resources.password_row_turned_off
import com.pasich.encly.core.resources.password_title
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/** "PIN": change it, or add one to a vault that has only a password. */
@Composable
internal fun PinRow(hasPin: Boolean, onClick: () -> Unit) {
    EnclyNavigationRow(
        title = stringResource(Res.string.auth_method_pin_title),
        supporting = stringResource(
            if (hasPin) Res.string.auth_method_pin_desc else Res.string.auth_method_pin_none,
        ),
        icon = EnclyIcons.Key,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/** "Password": another way to unlock; after a key reset it says that it was turned off. */
@Composable
internal fun PasswordRow(hasPassword: Boolean, turnedOff: Boolean, onClick: () -> Unit) {
    EnclyNavigationRow(
        title = stringResource(Res.string.password_title),
        supporting = stringResource(
            when {
                hasPassword -> Res.string.password_row_on
                turnedOff -> Res.string.password_row_turned_off
                else -> Res.string.password_row_off
            },
        ),
        icon = EnclyIcons.Lock,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/** The wipe PIN and the duress PIN can only be PINs: a password is never one of them. */
@Composable
internal fun PinOnlyNote() {
    Text(
        text = stringResource(Res.string.password_pin_only_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
