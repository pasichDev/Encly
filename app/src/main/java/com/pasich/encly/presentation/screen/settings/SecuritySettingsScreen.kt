package com.pasich.encly.presentation.screen.settings

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.composables.icons.lucide.FileKey
import com.composables.icons.lucide.Fingerprint
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyListRow
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.EnclyTextButton
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

@Composable
fun SecuritySettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = hiltViewModel(),
    vaultViewModel: BackupViewModel = hiltViewModel(),
) {
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity

    // Re-authentication, the new-phrase steps and the erase confirmation.
    BackupDialogs(rememberBackupDialogActions(vaultViewModel), vaultState.step)
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
    SecurityScreenEffects(vaultState.message, vaultState.erased, securityState.error, effectActions)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(R.string.security_title), onBack = { navController.popBackStack() })
        },
    ) { paddingValues ->
        SecurityContent(
            securityState = securityState,
            vaultBusy = vaultState.busy,
            actions = SecurityActions(
                onChangePin = { navController.navigate(NavRoutes.PinCodeConfig.name) },
                onBiometric = { enabled -> if (activity != null) securityViewModel.toggleBiometric(activity, enabled) },
                onVaultAction = vaultViewModel::start,
            ),
            modifier = Modifier.padding(paddingValues),
        )
    }
}

/** What the security page can start. */
private class SecurityActions(
    val onChangePin: () -> Unit,
    val onBiometric: (Boolean) -> Unit,
    val onVaultAction: (BackupAction) -> Unit,
)

@Composable
private fun SecurityContent(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    vaultBusy: Boolean,
    actions: SecurityActions,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
    ) {
        EnclyCallout(
            title = stringResource(R.string.security_encryption_active),
            text = stringResource(
                if (securityState.isUserCreatedSeedKey) {
                    R.string.security_encryption_seed_desc
                } else {
                    R.string.security_encryption_pin_desc
                },
            ),
            icon = Lucide.Lock,
        )
        SectionOverline(
            text = stringResource(R.string.auth_title),
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
        EnclyGroup {
            EnclyNavigationRow(
                title = stringResource(R.string.auth_method_pin_title),
                supporting = stringResource(R.string.auth_method_pin_desc),
                icon = Lucide.KeyRound,
                onClick = actions.onChangePin,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
            )
            if (securityState.authType != AuthType.NONE) {
                EnclyGroupDivider()
                BiometricSetting(securityState, actions.onBiometric)
            }
            EnclyGroupDivider()
            RecoveryPhraseRow(
                hasRecoveryPhrase = securityState.isUserCreatedSeedKey,
                busy = vaultBusy,
                onStart = actions.onVaultAction,
            )
        }
        EraseSection(busy = vaultBusy, onStart = actions.onVaultAction)
        EnclyCallout(
            title = stringResource(R.string.security_info_title),
            text = stringResource(R.string.security_info_body),
        )
    }
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
    actions: SecurityEffectActions,
) {
    val context = LocalContext.current

    LaunchedEffect(vaultMessage) {
        val message = vaultMessage as? BackupMessage.Text ?: return@LaunchedEffect
        actions.onVaultMessageShown()
        Toast.makeText(context, message.id, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(vaultErased) {
        if (vaultErased) actions.onVaultErased()
    }

    LaunchedEffect(securityError) {
        securityError?.let { errorMessage ->
            Toast.makeText(context, errorMessage.asString(context), Toast.LENGTH_LONG).show()
            actions.onErrorShown()
        }
    }
}

@Composable
private fun BiometricSetting(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    onToggle: (Boolean) -> Unit,
) {
    EnclySwitchRow(
        title = stringResource(R.string.security_biometric_title),
        supporting = stringResource(
            when (securityState.biometricStatus) {
                BiometricStatus.AVAILABLE -> R.string.security_biometric_available

                // The sensor is there: say what is missing, not that the device cannot do it.
                BiometricStatus.NOT_ENROLLED -> R.string.security_biometric_not_enrolled

                BiometricStatus.UNAVAILABLE -> R.string.security_biometric_unavailable
            },
        ),
        icon = Lucide.Fingerprint,
        checked = securityState.biometricEnable,
        enabled = securityState.isBiometricAvailable,
        onCheckedChange = onToggle,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/**
 * Create a recovery phrase later: a vault set up without one can otherwise only be wiped when
 * the PIN is forgotten. It starts with re-authentication.
 */
@Composable
private fun RecoveryPhraseRow(hasRecoveryPhrase: Boolean, busy: Boolean, onStart: (BackupAction) -> Unit) {
    EnclyListRow(
        title = stringResource(R.string.backup_needs_phrase_title),
        supporting = stringResource(
            if (hasRecoveryPhrase) R.string.security_recovery_set else R.string.security_recovery_missing,
        ),
        icon = Lucide.FileKey,
        onClick = { if (!hasRecoveryPhrase && !busy) onStart(BackupAction.CREATE_PHRASE) },
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    )
}

/** Erasing all data: a warning callout and an `error` text button, confirmed in a dialog. */
@Composable
private fun EraseSection(busy: Boolean, onStart: (BackupAction) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        modifier = Modifier.padding(top = EnclyTheme.spacing.s),
    ) {
        EnclyCallout(
            title = stringResource(R.string.security_erase_title),
            text = stringResource(R.string.security_erase_desc),
            tone = CalloutTone.WARNING,
        )
        EnclyTextButton(
            text = stringResource(R.string.security_erase_title),
            onClick = { if (!busy) onStart(BackupAction.ERASE) },
            destructive = true,
            icon = Lucide.Trash2,
        )
    }
}
