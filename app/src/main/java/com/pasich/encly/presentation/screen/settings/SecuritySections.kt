package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyHoldToConfirmButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyListRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.ui.theme.EnclyTheme

/** Erasing everything is held for five seconds, then re-authenticated. */
private const val ERASE_HOLD_MS = 5_000

/** How Encly protects the notes: three facts in a group, not a wall of bullets. */
@Composable
internal fun ProtectionSection(modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s), modifier = modifier) {
        SectionOverline(
            text = stringResource(R.string.security_protection_section),
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
        EnclyGroup {
            EnclyListRow(
                title = stringResource(R.string.security_protection_key_title),
                supporting = stringResource(R.string.security_protection_key_desc),
                icon = EnclyIcons.Key,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
            EnclyGroupDivider()
            EnclyListRow(
                title = stringResource(R.string.security_protection_pin_title),
                supporting = stringResource(R.string.security_protection_pin_desc),
                icon = EnclyIcons.Shield,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
            EnclyGroupDivider()
            EnclyListRow(
                title = stringResource(R.string.security_protection_slots_title),
                supporting = stringResource(R.string.security_protection_slots_desc),
                icon = EnclyIcons.Fingerprint,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
        }
    }
}

/**
 * The danger zone, last on the page: what erasing does, and a full-width `error` button held
 * for five seconds. Then a warning dialog, and only after it the PIN or fingerprint ([onErase]
 * starts the re-authenticated erase).
 */
@Composable
internal fun EraseSection(busy: Boolean, onErase: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        EnclyDialog(
            title = stringResource(R.string.security_erase_confirm_title),
            text = stringResource(R.string.security_erase_confirm_body),
            onDismissRequest = { confirming = false },
            confirm = DialogAction(
                stringResource(R.string.security_erase_confirm),
                {
                    confirming = false
                    onErase()
                },
                destructive = true,
            ),
            dismiss = DialogAction(stringResource(R.string.cancel), { confirming = false }),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s), modifier = modifier) {
        SectionOverline(
            text = stringResource(R.string.security_danger_section),
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = colors.surface,
            border = BorderStroke(EnclyTheme.spacing.hairline, colors.error),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
                modifier = Modifier.padding(EnclyTheme.spacing.m),
            ) {
                Text(
                    text = stringResource(R.string.security_erase_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.error,
                )
                Text(
                    text = stringResource(R.string.security_erase_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                EnclyHoldToConfirmButton(
                    text = stringResource(R.string.security_erase_hold),
                    onConfirm = { if (!busy) confirming = true },
                    holdMillis = ERASE_HOLD_MS,
                )
            }
        }
    }
}
