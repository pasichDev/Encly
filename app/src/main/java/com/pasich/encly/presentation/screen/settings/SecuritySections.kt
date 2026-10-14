package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.BackupAction
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

/**
 * "Wipe PIN": always the same row, set or not (only the wipe PIN itself could tell). After a PIN
 * key reset it says that any wipe PIN was turned off.
 */
@Composable
internal fun WipePinRow(turnedOff: Boolean, onClick: () -> Unit) {
    EnclyNavigationRow(
        title = stringResource(R.string.wipe_pin_title),
        supporting = stringResource(if (turnedOff) R.string.wipe_pin_row_turned_off else R.string.wipe_pin_row_desc),
        icon = EnclyIcons.Trash,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/** "Duress PIN": opens a separate vault; after a PIN key reset it says that it was turned off. */
@Composable
internal fun DuressPinRow(turnedOff: Boolean, onClick: () -> Unit) {
    EnclyNavigationRow(
        title = stringResource(R.string.duress_pin_title),
        supporting = stringResource(
            if (turnedOff) R.string.duress_pin_row_turned_off else R.string.duress_pin_row_desc,
        ),
        icon = EnclyIcons.Shield,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/**
 * The recovery phrase: once set up it is a status (it is never shown again; a separate row
 * replaces it with new words).
 * Without one the row creates it, after re-authentication: a vault set up without one can
 * otherwise only be wiped when the PIN is forgotten.
 */
@Composable
internal fun RecoveryPhraseRow(hasRecoveryPhrase: Boolean, busy: Boolean, onStart: (BackupAction) -> Unit) {
    val title = stringResource(R.string.backup_needs_phrase_title)
    val modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s)
    if (hasRecoveryPhrase) {
        EnclyListRow(
            title = title,
            supporting = stringResource(R.string.security_recovery_set_desc),
            icon = EnclyIcons.File,
            modifier = modifier,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            ) {
                Icon(
                    EnclyIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(EnclyTheme.spacing.iconXSmall),
                )
                Text(
                    text = stringResource(R.string.security_recovery_status_set),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else {
        EnclyNavigationRow(
            title = title,
            supporting = stringResource(R.string.security_recovery_missing),
            icon = EnclyIcons.File,
            onClick = { if (!busy) onStart(BackupAction.CREATE_PHRASE) },
            modifier = modifier,
        )
    }
}

/**
 * "Replace recovery phrase": re-authentication, a warning that backups made before keep needing
 * the old words, then new words shown and checked like the first ones.
 */
@Composable
internal fun ReplacePhraseRow(busy: Boolean, onStart: (BackupAction) -> Unit) {
    EnclyNavigationRow(
        title = stringResource(R.string.security_recovery_replace_title),
        supporting = stringResource(R.string.security_recovery_replace_desc),
        icon = EnclyIcons.Restore,
        onClick = { if (!busy) onStart(BackupAction.REPLACE_PHRASE) },
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/** Privacy options that are not about unlocking: strict keyboard privacy (off by default). */
@Composable
internal fun PrivacySection(strictKeyboard: Boolean, onStrictKeyboard: (Boolean) -> Unit) {
    SectionOverline(
        text = stringResource(R.string.security_privacy_section),
        modifier = Modifier.padding(top = EnclyTheme.spacing.s),
    )
    EnclyGroup {
        EnclySwitchRow(
            title = stringResource(R.string.security_keyboard_strict_title),
            supporting = stringResource(R.string.security_keyboard_strict_desc),
            icon = EnclyIcons.Keyboard,
            checked = strictKeyboard,
            onCheckedChange = onStrictKeyboard,
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
        )
    }
}
