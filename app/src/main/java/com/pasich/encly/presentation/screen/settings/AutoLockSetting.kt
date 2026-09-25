package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclyRadioRow
import com.pasich.encly.ui.theme.EnclyTheme

/** "Lock after leaving": the current delay; a tap opens the choice. */
@Composable
internal fun AutoLockRow(current: AutoLockDelay, onSelect: (AutoLockDelay) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    EnclyNavigationRow(
        title = stringResource(R.string.auto_lock_title),
        supporting = stringResource(current.label()),
        icon = EnclyIcons.Lock,
        onClick = { choosing = true },
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
    if (choosing) {
        AutoLockDialog(
            current = current,
            onDismiss = { choosing = false },
            onConfirm = {
                onSelect(it)
                choosing = false
            },
        )
    }
}

@Composable
private fun AutoLockDialog(current: AutoLockDelay, onDismiss: () -> Unit, onConfirm: (AutoLockDelay) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    EnclyDialog(
        title = stringResource(R.string.auto_lock_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.done), { onConfirm(selected) }),
        dismiss = DialogAction(stringResource(R.string.cancel), onDismiss),
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            Text(
                text = stringResource(R.string.auto_lock_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = EnclyTheme.spacing.xs),
            )
            AutoLockDelay.entries.forEach { delay ->
                EnclyRadioRow(
                    title = stringResource(delay.label()),
                    selected = delay == selected,
                    onClick = { selected = delay },
                )
            }
        }
    }
}

private fun AutoLockDelay.label(): Int = when (this) {
    AutoLockDelay.IMMEDIATELY -> R.string.auto_lock_immediately
    AutoLockDelay.SECONDS_15 -> R.string.auto_lock_15s
    AutoLockDelay.SECONDS_30 -> R.string.auto_lock_30s
    AutoLockDelay.MINUTE_1 -> R.string.auto_lock_1m
    AutoLockDelay.MINUTES_2 -> R.string.auto_lock_2m
}
