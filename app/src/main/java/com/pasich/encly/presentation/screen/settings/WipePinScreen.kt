package com.pasich.encly.presentation.screen.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.security.WipePinChange
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel

private val WipePinTexts = PinFlowTexts(
    title = R.string.wipe_pin_title,
    currentSubtitle = R.string.wipe_pin_current_subtitle,
    newTitle = R.string.wipe_pin_new_title,
    newSubtitle = R.string.wipe_pin_new_subtitle,
    confirmTitle = R.string.wipe_pin_confirm_title,
    confirmSubtitle = R.string.wipe_pin_confirm_subtitle,
)

/**
 * Settings → Security → Wipe PIN. The PIN comes first; then the page explains what a wipe PIN
 * does and offers to set one (replacing any earlier one) or turn it off. It never says whether
 * one is set: only the wipe PIN itself could tell (see AuthenticationManager).
 */
@Composable
fun WipePinScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = metroViewModel(),
) {
    val state = remember { PinFlowState() }
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val flowActions = remember(state, securityViewModel) {
        PinFlowActions(
            verify = securityViewModel::verifyCurrentPin,
            lockoutMillis = securityViewModel::pinLockoutRemainingMillis,
            save = { pin -> securityViewModel.setWipePin(pin) { result -> state.onWipePinResult(result) } },
            close = { navController.popBackStack() },
        )
    }

    PinFlowScaffold(
        state = state,
        texts = WipePinTexts,
        actions = flowActions,
        modifier = modifier,
    ) { paddingValues ->
        WipePinOptions(
            info = WipePinInfo(
                turnedOff = securityState.wipePinTurnedOff,
                biometricOn = securityState.biometricEnable,
            ),
            actions = WipePinActions(
                onSet = state::startNew,
                onRemove = {
                    securityViewModel.removeWipePin { ok ->
                        state.message = if (ok) R.string.wipe_pin_removed else R.string.wipe_pin_failed
                    }
                },
                onBiometricOff = {
                    if (activity != null) securityViewModel.toggleBiometric(activity, enable = false)
                },
            ),
            modifier = Modifier.padding(paddingValues),
        )
    }
}

private fun PinFlowState.onWipePinResult(result: WipePinChange) {
    when (result) {
        WipePinChange.SET -> saved(R.string.wipe_pin_saved)
        WipePinChange.SAME_AS_PIN -> restartNew(R.string.wipe_pin_same_as_pin)
        WipePinChange.IN_USE -> restartNew(R.string.pin_in_use)
        WipePinChange.FAILED -> restartNew(R.string.wipe_pin_failed)
    }
}

/** What the options page shows besides the actions. */
private class WipePinInfo(val turnedOff: Boolean, val biometricOn: Boolean)

private class WipePinActions(val onSet: () -> Unit, val onRemove: () -> Unit, val onBiometricOff: () -> Unit)

@Composable
private fun WipePinOptions(info: WipePinInfo, actions: WipePinActions, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.s),
    ) {
        if (info.turnedOff) {
            EnclyCallout(text = stringResource(R.string.wipe_pin_turned_off), tone = CalloutTone.WARNING)
        }
        Text(
            text = stringResource(R.string.wipe_pin_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnclyCallout(text = stringResource(R.string.wipe_pin_after_erase), tone = CalloutTone.WARNING)
        EnclyCallout(text = stringResource(R.string.wipe_pin_backups))
        if (info.biometricOn) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                EnclyCallout(text = stringResource(R.string.wipe_pin_biometric_warning), tone = CalloutTone.WARNING)
                EnclyTextButton(
                    text = stringResource(R.string.wipe_pin_biometric_off),
                    onClick = actions.onBiometricOff,
                )
            }
        }
        Text(
            text = stringResource(R.string.wipe_pin_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
        ) {
            EnclyButton(text = stringResource(R.string.wipe_pin_set), onClick = actions.onSet)
            EnclyTextButton(
                text = stringResource(R.string.wipe_pin_remove),
                onClick = actions.onRemove,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
