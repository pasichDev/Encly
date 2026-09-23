package com.pasich.encly.presentation.screen.settings

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.components.custombox.SettingBox
import com.pasich.encly.presentation.components.settings.AuthMethodSelector
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.backup.BackupDialogs
import com.pasich.encly.presentation.screen.backup.rememberBackupDialogActions
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.security_title)) }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        })
    }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            item { EncryptionStatusCard(hasRecoveryPhrase = securityState.isUserCreatedSeedKey) }

            item { Spacer(Modifier.height(20.dp)) }

            // Authorization
            item {
                Text(
                    text = stringResource(R.string.auth_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                AuthMethodSelector(
                    selected = securityState.authType,
                    onSelect = {
                        navController.navigate(NavRoutes.PinCodeConfig.name)
                    },
                )
            }
            item { Spacer(Modifier.height(15.dp)) }
            if (securityState.authType != AuthType.NONE) {
                item {
                    BiometricSetting(securityState) { enabled ->
                        if (activity != null) securityViewModel.toggleBiometric(activity, enabled)
                    }
                }
            }

            item { Spacer(Modifier.height(15.dp)) }
            item {
                VaultActions(
                    hasRecoveryPhrase = securityState.isUserCreatedSeedKey,
                    busy = vaultState.busy,
                    onStart = vaultViewModel::start,
                )
            }

            item { Spacer(Modifier.height(20.dp)) }

            // Security information
            item { SecurityInfoCard() }
        }
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
private fun EncryptionStatusCard(hasRecoveryPhrase: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(R.string.security_encryption_active),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (hasRecoveryPhrase) {
                        stringResource(R.string.security_encryption_seed_desc)
                    } else {
                        stringResource(R.string.security_encryption_pin_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BiometricSetting(
    securityState: SecuritySettingsViewModel.SecuritySettingsUiState,
    onToggle: (Boolean) -> Unit,
) {
    SettingBox(
        title = stringResource(R.string.security_biometric_title),
        subTitle = if (securityState.isBiometricAvailable) {
            stringResource(R.string.security_biometric_available)
        } else {
            stringResource(R.string.security_biometric_unavailable)
        },
        roundPosition = RoundPosition.Full,
        endWidget = {
            Switch(
                checked = securityState.biometricEnable,
                enabled = securityState.isBiometricAvailable,
                onCheckedChange = onToggle,
            )
        },
    )
}

/**
 * Create a recovery phrase later (a vault set up without one can otherwise only be wiped when
 * the PIN is forgotten) and erase all data. Both start with re-authentication.
 */
@Composable
private fun VaultActions(hasRecoveryPhrase: Boolean, busy: Boolean, onStart: (BackupAction) -> Unit) {
    Column {
        SettingBox(
            title = stringResource(R.string.backup_needs_phrase_title),
            subTitle = if (hasRecoveryPhrase) {
                stringResource(R.string.security_recovery_set)
            } else {
                stringResource(R.string.security_recovery_missing)
            },
            roundPosition = RoundPosition.First,
            icon = rememberVectorPainter(Lucide.KeyRound),
            action = { if (!hasRecoveryPhrase && !busy) onStart(BackupAction.CREATE_PHRASE) },
        )
        SettingBox(
            title = stringResource(R.string.security_erase_title),
            subTitle = stringResource(R.string.security_erase_desc),
            roundPosition = RoundPosition.Last,
            icon = rememberVectorPainter(Lucide.Trash2),
            action = { if (!busy) onStart(BackupAction.ERASE) },
        )
    }
}

@Composable
private fun SecurityInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.security_info_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.security_info_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
