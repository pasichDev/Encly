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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.pin_in_use
import com.pasich.encly.core.resources.wipe_pin_after_erase
import com.pasich.encly.core.resources.wipe_pin_backups
import com.pasich.encly.core.resources.wipe_pin_biometric_off
import com.pasich.encly.core.resources.wipe_pin_biometric_warning
import com.pasich.encly.core.resources.wipe_pin_confirm_subtitle
import com.pasich.encly.core.resources.wipe_pin_confirm_title
import com.pasich.encly.core.resources.wipe_pin_current_subtitle
import com.pasich.encly.core.resources.wipe_pin_failed
import com.pasich.encly.core.resources.wipe_pin_intro
import com.pasich.encly.core.resources.wipe_pin_limits
import com.pasich.encly.core.resources.wipe_pin_new_subtitle
import com.pasich.encly.core.resources.wipe_pin_new_title
import com.pasich.encly.core.resources.wipe_pin_remove
import com.pasich.encly.core.resources.wipe_pin_removed
import com.pasich.encly.core.resources.wipe_pin_same_as_pin
import com.pasich.encly.core.resources.wipe_pin_saved
import com.pasich.encly.core.resources.wipe_pin_set
import com.pasich.encly.core.resources.wipe_pin_title
import com.pasich.encly.core.resources.wipe_pin_turned_off
import com.pasich.encly.core.security.WipePinChange
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

private val WipePinTexts = PinFlowTexts(
    title = Res.string.wipe_pin_title,
    currentSubtitle = Res.string.wipe_pin_current_subtitle,
    newTitle = Res.string.wipe_pin_new_title,
    newSubtitle = Res.string.wipe_pin_new_subtitle,
    confirmTitle = Res.string.wipe_pin_confirm_title,
    confirmSubtitle = Res.string.wipe_pin_confirm_subtitle,
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
                        state.message = if (ok) Res.string.wipe_pin_removed else Res.string.wipe_pin_failed
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
        WipePinChange.SET -> saved(Res.string.wipe_pin_saved)
        WipePinChange.SAME_AS_PIN -> restartNew(Res.string.wipe_pin_same_as_pin)
        WipePinChange.IN_USE -> restartNew(Res.string.pin_in_use)
        WipePinChange.FAILED -> restartNew(Res.string.wipe_pin_failed)
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
            EnclyCallout(text = stringResource(Res.string.wipe_pin_turned_off), tone = CalloutTone.WARNING)
        }
        Text(
            text = stringResource(Res.string.wipe_pin_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnclyCallout(text = stringResource(Res.string.wipe_pin_after_erase), tone = CalloutTone.WARNING)
        EnclyCallout(text = stringResource(Res.string.wipe_pin_backups))
        if (info.biometricOn) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                EnclyCallout(text = stringResource(Res.string.wipe_pin_biometric_warning), tone = CalloutTone.WARNING)
                EnclyTextButton(
                    text = stringResource(Res.string.wipe_pin_biometric_off),
                    onClick = actions.onBiometricOff,
                )
            }
        }
        Text(
            text = stringResource(Res.string.wipe_pin_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
        ) {
            EnclyButton(text = stringResource(Res.string.wipe_pin_set), onClick = actions.onSet)
            EnclyTextButton(
                text = stringResource(Res.string.wipe_pin_remove),
                onClick = actions.onRemove,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
