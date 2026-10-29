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
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auto_lock_15m
import com.pasich.encly.core.resources.auto_lock_15s
import com.pasich.encly.core.resources.auto_lock_1m
import com.pasich.encly.core.resources.auto_lock_2m
import com.pasich.encly.core.resources.auto_lock_30m
import com.pasich.encly.core.resources.auto_lock_30s
import com.pasich.encly.core.resources.auto_lock_desc
import com.pasich.encly.core.resources.auto_lock_immediately
import com.pasich.encly.core.resources.auto_lock_long_delay_warning
import com.pasich.encly.core.resources.auto_lock_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclyRadioRow
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** "Lock after leaving": the current delay; a tap opens the choice. */
@Composable
internal fun AutoLockRow(current: AutoLockDelay, onSelect: (AutoLockDelay) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    EnclyNavigationRow(
        title = stringResource(Res.string.auto_lock_title),
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
        title = stringResource(Res.string.auto_lock_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(Res.string.done), { onConfirm(selected) }),
        dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            Text(
                text = stringResource(Res.string.auto_lock_desc),
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
            if (selected == AutoLockDelay.MINUTES_15 || selected == AutoLockDelay.MINUTES_30) {
                Text(
                    text = stringResource(Res.string.auto_lock_long_delay_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = EnclyTheme.spacing.xs),
                )
            }
        }
    }
}

private fun AutoLockDelay.label(): StringResource = when (this) {
    AutoLockDelay.IMMEDIATELY -> Res.string.auto_lock_immediately
    AutoLockDelay.SECONDS_15 -> Res.string.auto_lock_15s
    AutoLockDelay.SECONDS_30 -> Res.string.auto_lock_30s
    AutoLockDelay.MINUTE_1 -> Res.string.auto_lock_1m
    AutoLockDelay.MINUTES_2 -> Res.string.auto_lock_2m
    AutoLockDelay.MINUTES_15 -> Res.string.auto_lock_15m
    AutoLockDelay.MINUTES_30 -> Res.string.auto_lock_30m
}
