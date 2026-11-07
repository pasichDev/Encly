package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.duress_pin_change
import com.pasich.encly.core.resources.duress_pin_confirm_subtitle
import com.pasich.encly.core.resources.duress_pin_confirm_title
import com.pasich.encly.core.resources.duress_pin_current_subtitle
import com.pasich.encly.core.resources.duress_pin_failed
import com.pasich.encly.core.resources.duress_pin_intro
import com.pasich.encly.core.resources.duress_pin_limits
import com.pasich.encly.core.resources.duress_pin_new_subtitle
import com.pasich.encly.core.resources.duress_pin_new_title
import com.pasich.encly.core.resources.duress_pin_real_vault
import com.pasich.encly.core.resources.duress_pin_remove
import com.pasich.encly.core.resources.duress_pin_remove_confirm
import com.pasich.encly.core.resources.duress_pin_remove_confirm_body
import com.pasich.encly.core.resources.duress_pin_remove_confirm_title
import com.pasich.encly.core.resources.duress_pin_removed
import com.pasich.encly.core.resources.duress_pin_same_as_pin
import com.pasich.encly.core.resources.duress_pin_saved
import com.pasich.encly.core.resources.duress_pin_saved_kept
import com.pasich.encly.core.resources.duress_pin_set
import com.pasich.encly.core.resources.duress_pin_status_off
import com.pasich.encly.core.resources.duress_pin_status_on
import com.pasich.encly.core.resources.duress_pin_title
import com.pasich.encly.core.resources.duress_pin_turned_off
import com.pasich.encly.core.resources.pin_in_use
import com.pasich.encly.core.security.DuressPinChange
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyScrollColumn
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

private val DuressPinTexts = PinFlowTexts(
    title = Res.string.duress_pin_title,
    currentSubtitle = Res.string.duress_pin_current_subtitle,
    newTitle = Res.string.duress_pin_new_title,
    newSubtitle = Res.string.duress_pin_new_subtitle,
    confirmTitle = Res.string.duress_pin_confirm_title,
    confirmSubtitle = Res.string.duress_pin_confirm_subtitle,
)

/**
 * Settings → Security → Duress PIN. Same flow as the wipe PIN: the real PIN first, then what the
 * duress PIN does and whether one is set, then the new PIN typed twice. In a decoy session it
 * opens too, and setting or turning it off fails like a storage error (see SecurityManager).
 */
@Composable
fun DuressPinScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = metroViewModel(),
) {
    val state = remember { PinFlowState() }
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val flowActions = remember(state, securityViewModel) {
        PinFlowActions(
            verify = securityViewModel::verifyCurrentPin,
            lockoutMillis = securityViewModel::pinLockoutRemainingMillis,
            save = { pin -> securityViewModel.setDuressPin(pin) { result -> state.onDuressPinResult(result) } },
            close = { navController.popBackStack() },
        )
    }

    PinFlowScaffold(
        state = state,
        texts = DuressPinTexts,
        actions = flowActions,
        modifier = modifier,
    ) { paddingValues ->
        DuressPinOptions(
            info = DuressPinInfo(turnedOff = securityState.duressPinTurnedOff, isSet = securityState.hasDuressPin),
            actions = DuressPinActions(
                onSet = state::startNew,
                onRemove = {
                    securityViewModel.removeDuressPin { ok ->
                        state.message = if (ok) Res.string.duress_pin_removed else Res.string.duress_pin_failed
                    }
                },
            ),
            modifier = Modifier.padding(paddingValues),
        )
    }
}

private fun PinFlowState.onDuressPinResult(result: DuressPinChange) {
    when (result) {
        DuressPinChange.SET -> saved(Res.string.duress_pin_saved_kept)
        DuressPinChange.SET_NEW_VAULT -> saved(Res.string.duress_pin_saved)
        DuressPinChange.SAME_AS_PIN -> restartNew(Res.string.duress_pin_same_as_pin)
        DuressPinChange.IN_USE -> restartNew(Res.string.pin_in_use)
        DuressPinChange.FAILED -> restartNew(Res.string.duress_pin_failed)
    }
}

/** What the options page shows besides the actions; [isSet]: a decoy vault exists, live or [turnedOff]. */
private class DuressPinInfo(val turnedOff: Boolean, val isSet: Boolean)

private class DuressPinActions(val onSet: () -> Unit, val onRemove: () -> Unit)

@Composable
private fun DuressPinInfo.statusText(): String = stringResource(
    if (isSet && !turnedOff) Res.string.duress_pin_status_on else Res.string.duress_pin_status_off,
)

@Composable
private fun DuressPinOptions(info: DuressPinInfo, actions: DuressPinActions, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmingRemove) {
        RemoveDuressPinDialog(
            onConfirm = {
                confirmingRemove = false
                actions.onRemove()
            },
            onDismiss = { confirmingRemove = false },
        )
    }
    EnclyScrollColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.s),
    ) {
        if (info.turnedOff) {
            EnclyCallout(text = stringResource(Res.string.duress_pin_turned_off), tone = CalloutTone.WARNING)
        }
        Text(
            text = stringResource(Res.string.duress_pin_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnclyCallout(text = stringResource(Res.string.duress_pin_real_vault))
        Text(
            text = stringResource(Res.string.duress_pin_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = info.statusText(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
        ) {
            EnclyButton(
                text = stringResource(if (info.isSet) Res.string.duress_pin_change else Res.string.duress_pin_set),
                onClick = actions.onSet,
            )
            if (info.isSet) {
                EnclyTextButton(
                    text = stringResource(Res.string.duress_pin_remove),
                    onClick = { confirmingRemove = true },
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Turning it off deletes the decoy vault; the real vault is not affected. */
@Composable
private fun RemoveDuressPinDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    EnclyDialog(
        title = stringResource(Res.string.duress_pin_remove_confirm_title),
        text = stringResource(Res.string.duress_pin_remove_confirm_body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(Res.string.duress_pin_remove_confirm), onConfirm, destructive = true),
        dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
    )
}
