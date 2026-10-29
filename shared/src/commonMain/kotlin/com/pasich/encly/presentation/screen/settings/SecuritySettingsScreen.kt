package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.common.resolve
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auth_method_pin_desc
import com.pasich.encly.core.resources.auth_method_pin_title
import com.pasich.encly.core.resources.auth_title
import com.pasich.encly.core.resources.security_biometric_available
import com.pasich.encly.core.resources.security_biometric_not_enrolled
import com.pasich.encly.core.resources.security_biometric_title
import com.pasich.encly.core.resources.security_biometric_unavailable
import com.pasich.encly.core.resources.security_encryption_active
import com.pasich.encly.core.resources.security_encryption_pin_plain
import com.pasich.encly.core.resources.security_encryption_seed_plain
import com.pasich.encly.core.resources.security_title
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclyScrollColumn
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.backup.BackupDialogs
import com.pasich.encly.presentation.screen.backup.rememberBackupDialogActions
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun SecuritySettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = metroViewModel(),
    vaultViewModel: BackupViewModel = metroViewModel(),
) {
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val strictKeyboard by securityViewModel.strictKeyboard.collectAsStateWithLifecycle()
    val autoLockDelay by securityViewModel.autoLockDelay.collectAsStateWithLifecycle()
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val host = LocalBiometricHost.current

    val snackbarHostState = remember { SnackbarHostState() }
    val dialogActions = rememberBackupDialogActions(vaultViewModel)
    val effectActions = remember(navController, securityViewModel, vaultViewModel) {
        SecurityEffectActions(
            onVaultMessageShown = {
                vaultViewModel.clearMessage()
                securityViewModel.refresh()
            },
            onVaultErased = {
                navController.navigate(NavRoutes.OnboardingRoute.name) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onErrorShown = securityViewModel::clearError,
        )
    }
    SecurityScreenEffects(vaultState.message, vaultState.erased, securityState.error, snackbarHostState, effectActions)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                EnclyTopBar(title = stringResource(Res.string.security_title), onBack = {
                    navController.popBackStack()
                })
            },
            snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            // Nothing until the first load: no PIN text that turns into the phrase text.
            if (securityState.loaded) {
                SecurityContent(
                    securityState = securityState,
                    options = SecurityOptions(strictKeyboard = strictKeyboard, autoLock = autoLockDelay),
                    vaultBusy = vaultState.busy,
                    actions = SecurityActions(
                        onOpen = { route -> navController.navigate(route.name) },
                        onBiometric = { enabled ->
                            if (host != null) securityViewModel.toggleBiometric(host, enabled)
                        },
                        onVaultAction = vaultViewModel::start,
                        onStrictKeyboard = securityViewModel::setStrictKeyboard,
                        onAutoLock = securityViewModel::setAutoLockDelay,
                    ),
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
        // Re-authentication, the new-phrase steps and the erase confirmation; the phrase steps
        // cover the whole screen, so they are drawn last.
        BackupDialogs(dialogActions, vaultState.step, vaultState.busy)
    }
}

/** What the security page can start. */
private class SecurityActions(
    val onOpen: (NavRoutes) -> Unit,
    val onBiometric: (Boolean) -> Unit,
    val onVaultAction: (BackupAction) -> Unit,
    val onStrictKeyboard: (Boolean) -> Unit,
    val onAutoLock: (AutoLockDelay) -> Unit,
)

/** The page's own preferences: strict keyboard privacy and the auto-lock delay. */
private class SecurityOptions(val strictKeyboard: Boolean, val autoLock: AutoLockDelay)

@Composable
private fun SecurityContent(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    options: SecurityOptions,
    vaultBusy: Boolean,
    actions: SecurityActions,
    modifier: Modifier = Modifier,
) {
    EnclyScrollColumn(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
    ) {
        SecurityRows(securityState, options, vaultBusy, actions)
    }
}

@Composable
private fun SecurityRows(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    options: SecurityOptions,
    vaultBusy: Boolean,
    actions: SecurityActions,
) {
    EncryptionStatusCard(
        title = stringResource(Res.string.security_encryption_active),
        text = stringResource(
            if (securityState.isUserCreatedSeedKey) {
                Res.string.security_encryption_seed_plain
            } else {
                Res.string.security_encryption_pin_plain
            },
        ),
    )
    SectionOverline(
        text = stringResource(Res.string.auth_title),
        modifier = Modifier.padding(top = EnclyTheme.spacing.s),
    )
    EnclyGroup {
        EnclyNavigationRow(
            title = stringResource(Res.string.auth_method_pin_title),
            supporting = stringResource(Res.string.auth_method_pin_desc),
            icon = EnclyIcons.Key,
            onClick = { actions.onOpen(NavRoutes.PinCodeConfig) },
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
        )
        if (securityState.authType != AuthType.NONE) {
            EnclyGroupDivider()
            BiometricSetting(securityState, actions.onBiometric)
        }
        if (securityState.hasPinSlot) {
            EnclyGroupDivider()
            WipePinRow(turnedOff = securityState.wipePinTurnedOff, onClick = { actions.onOpen(NavRoutes.WipePinRoute) })
            EnclyGroupDivider()
            DuressPinRow(turnedOff = securityState.duressPinTurnedOff, onClick = {
                actions.onOpen(NavRoutes.DuressPinRoute)
            })
        }
        EnclyGroupDivider()
        AutoLockRow(current = options.autoLock, onSelect = actions.onAutoLock)
        EnclyGroupDivider()
        RecoveryPhraseRow(
            hasRecoveryPhrase = securityState.isUserCreatedSeedKey,
            busy = vaultBusy,
            onStart = actions.onVaultAction,
        )
        if (securityState.isUserCreatedSeedKey) {
            EnclyGroupDivider()
            ReplacePhraseRow(busy = vaultBusy, onStart = actions.onVaultAction)
        }
    }
    PrivacySection(strictKeyboard = options.strictKeyboard, onStrictKeyboard = actions.onStrictKeyboard)
    ProtectionSection()
    EraseSection(busy = vaultBusy, onErase = { actions.onVaultAction(BackupAction.ERASE) })
}

private class SecurityEffectActions(
    val onVaultMessageShown: () -> Unit,
    val onVaultErased: () -> Unit,
    val onErrorShown: () -> Unit,
)

/** Messages and the end of the vault flows (a new recovery phrase, an erased vault), and errors. */
@Composable
private fun SecurityScreenEffects(
    vaultMessage: BackupMessage?,
    vaultErased: Boolean,
    securityError: UiText?,
    snackbarHostState: SnackbarHostState,
    actions: SecurityEffectActions,
) {
    val vaultMessageText = (vaultMessage as? BackupMessage.Text)?.let { stringResource(it.id) }
    // Clearing a message restarts its effect, which must not cancel the snackbar it just opened.
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(vaultMessage, vaultMessageText) {
        val message = vaultMessageText ?: return@LaunchedEffect
        actions.onVaultMessageShown()
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(vaultErased) {
        if (vaultErased) actions.onVaultErased()
    }

    LaunchedEffect(securityError) {
        val errorMessage = securityError ?: return@LaunchedEffect
        actions.onErrorShown()
        snackbarScope.launch { snackbarHostState.showSnackbar(errorMessage.resolve()) }
    }
}

@Composable
private fun BiometricSetting(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    onToggle: (Boolean) -> Unit,
) {
    EnclySwitchRow(
        title = stringResource(Res.string.security_biometric_title),
        supporting = stringResource(
            when (securityState.biometricStatus) {
                BiometricStatus.AVAILABLE -> Res.string.security_biometric_available

                // The sensor is there: say what is missing, not that the device cannot do it.
                BiometricStatus.NOT_ENROLLED -> Res.string.security_biometric_not_enrolled

                BiometricStatus.UNAVAILABLE -> Res.string.security_biometric_unavailable
            },
        ),
        icon = EnclyIcons.Fingerprint,
        checked = securityState.biometricEnable,
        enabled = securityState.isBiometricAvailable,
        onCheckedChange = onToggle,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}
