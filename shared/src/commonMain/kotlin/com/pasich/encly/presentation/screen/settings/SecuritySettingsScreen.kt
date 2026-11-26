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
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.platform.BiometricWording
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.platform.LocalBiometricWording
import com.pasich.encly.platform.LocalScreenshotAllowance
import com.pasich.encly.platform.ScreenBiometricHost
import com.pasich.encly.platform.hostString
import com.pasich.encly.platform.secretString
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
import com.pasich.encly.presentation.navigation.openRoute
import com.pasich.encly.presentation.navigation.popBack
import com.pasich.encly.presentation.screen.backup.BackupDialogs
import com.pasich.encly.presentation.screen.backup.rememberBackupDialogActions
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.presentation.viewmodel.changeAutoLock
import com.pasich.encly.presentation.viewmodel.changeBiometric
import com.pasich.encly.presentation.viewmodel.changeNotePreviews
import com.pasich.encly.presentation.viewmodel.changeStrictKeyboard
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
    val options = rememberSecurityOptions(securityViewModel, vaultViewModel)
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
                    navController.popBack()
                })
            },
            snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            // Nothing until the first load: no PIN text that turns into the phrase text.
            if (securityState.loaded) {
                SecurityContent(
                    securityState = securityState,
                    options = options,
                    vaultBusy = vaultState.busy,
                    actions = securityActions(navController, securityViewModel, vaultViewModel, host),
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
        // Re-authentication, the new-phrase steps and the erase confirmation; the phrase steps
        // cover the whole screen, so they are drawn last.
        BackupDialogs(dialogActions, vaultState.step, vaultState.busy)
    }
}

private fun securityActions(
    navController: NavHostController,
    securityViewModel: SecuritySettingsViewModel,
    vaultViewModel: BackupViewModel,
    host: ScreenBiometricHost?,
) = SecurityActions(
    onOpen = { route -> navController.openRoute(route.name) },
    onBiometric = { enabled ->
        if (host != null) {
            vaultViewModel.changeBiometric(enabled) { securityViewModel.toggleBiometric(host, enabled) }
        }
    },
    onVaultAction = vaultViewModel::start,
    onStrictKeyboard = { strict ->
        vaultViewModel.changeStrictKeyboard(strict) { securityViewModel.setStrictKeyboard(strict) }
    },
    onAutoLock = { delay ->
        vaultViewModel.changeAutoLock(securityViewModel.autoLockDelay.value, delay) {
            securityViewModel.setAutoLockDelay(delay)
        }
    },
    onNotePreviews = vaultViewModel::changeNotePreviews,
)

/** What the security page can start. */
private class SecurityActions(
    val onOpen: (NavRoutes) -> Unit,
    val onBiometric: (Boolean) -> Unit,
    val onVaultAction: (BackupAction) -> Unit,
    val onStrictKeyboard: (Boolean) -> Unit,
    val onAutoLock: (AutoLockDelay) -> Unit,
    val onNotePreviews: (Boolean, () -> Unit) -> Unit,
)

/** The page's own preferences: strict keyboard privacy, the auto-lock delay and the screenshots allowance. */
private class SecurityOptions(
    val strictKeyboard: Boolean,
    val autoLock: AutoLockDelay,
    val screenshots: ScreenshotsSetting?,
    /** What the vault's secret is: its rows and descriptions say "PIN" or "passphrase". */
    val secretKind: SecretKind,
)

@Composable
private fun rememberSecurityOptions(
    securityViewModel: SecuritySettingsViewModel,
    vaultViewModel: BackupViewModel,
): SecurityOptions {
    val strictKeyboard by securityViewModel.strictKeyboard.collectAsStateWithLifecycle()
    val autoLockDelay by securityViewModel.autoLockDelay.collectAsStateWithLifecycle()
    val screenshotsAllowed by securityViewModel.screenshotsAllowed.collectAsStateWithLifecycle()
    // Only where the host can lift capture protection at all (Android); elsewhere the row is not shown.
    val screenshotsOffered = LocalScreenshotAllowance.current != null
    return SecurityOptions(
        strictKeyboard = strictKeyboard,
        autoLock = autoLockDelay,
        screenshots = ScreenshotsSetting(
            allowed = screenshotsAllowed,
            onTurnOn = { vaultViewModel.start(BackupAction.ALLOW_SCREENSHOTS) },
            onTurnOff = securityViewModel::stopAllowingScreenshots,
        ).takeIf { screenshotsOffered },
        secretKind = securityViewModel.secretKind,
    )
}

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

/** The wipe and the duress secret rows, each under a divider. */
@Composable
private fun SecondSecretRows(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    kind: SecretKind,
    actions: SecurityActions,
) {
    EnclyGroupDivider()
    WipePinRow(
        turnedOff = securityState.wipePinTurnedOff,
        kind = kind,
        onClick = { actions.onOpen(NavRoutes.WipePinRoute) },
    )
    EnclyGroupDivider()
    DuressPinRow(
        turnedOff = securityState.duressPinTurnedOff,
        kind = kind,
        onClick = { actions.onOpen(NavRoutes.DuressPinRoute) },
    )
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
        text = secretString(
            options.secretKind,
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
            title = secretString(options.secretKind, Res.string.auth_method_pin_title),
            supporting = secretString(options.secretKind, Res.string.auth_method_pin_desc),
            icon = EnclyIcons.Key,
            onClick = { actions.onOpen(NavRoutes.PinCodeConfig) },
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
        )
        // A host without biometrics (the desktop) shows no switch that could never be turned on.
        if (securityState.authType != AuthType.NONE && LocalBiometricWording.current != BiometricWording.None) {
            EnclyGroupDivider()
            BiometricSetting(securityState, actions.onBiometric)
        }
        if (securityState.hasPinSlot) SecondSecretRows(securityState, options.secretKind, actions)
        EnclyGroupDivider()
        AutoLockRow(current = options.autoLock, onSelect = actions.onAutoLock)
        EnclyGroupDivider()
        RecoveryPhraseRow(
            hasRecoveryPhrase = securityState.isUserCreatedSeedKey,
            kind = options.secretKind,
            busy = vaultBusy,
            onStart = actions.onVaultAction,
        )
        if (securityState.isUserCreatedSeedKey) {
            EnclyGroupDivider()
            ReplacePhraseRow(busy = vaultBusy, onStart = actions.onVaultAction)
        }
    }
    PrivacySection(
        strictKeyboard = options.strictKeyboard,
        onStrictKeyboard = actions.onStrictKeyboard,
        screenshots = options.screenshots,
    )
    NotePreviewsSetting(onChange = actions.onNotePreviews)
    ProtectionSection(options.secretKind)
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
        supporting = biometricStatusText(securityState.biometricStatus),
        icon = EnclyIcons.Fingerprint,
        checked = securityState.biometricEnable,
        enabled = securityState.isBiometricAvailable,
        onCheckedChange = onToggle,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

@Composable
private fun biometricStatusText(status: BiometricStatus): String {
    val wording = LocalBiometricWording.current
    return when (status) {
        // A named method (Face ID) says its own name; Android says "Fingerprint".
        BiometricStatus.AVAILABLE ->
            if (wording is BiometricWording.Named) {
                wording.name
            } else {
                stringResource(Res.string.security_biometric_available)
            }

        // The sensor is there: say what is missing, not that the device cannot do it.
        BiometricStatus.NOT_ENROLLED -> hostString(Res.string.security_biometric_not_enrolled)

        BiometricStatus.UNAVAILABLE -> stringResource(Res.string.security_biometric_unavailable)
    }
}
