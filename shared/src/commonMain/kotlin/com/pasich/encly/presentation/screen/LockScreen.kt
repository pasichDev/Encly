package com.pasich.encly.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.platform.LocalSessionSharedAcrossWindows
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.presentation.effects.UnlockRevealState
import com.pasich.encly.presentation.effects.revealThen
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.viewmodel.LockViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/** The app's lock screen: unlocking opens Home (or the note that was open), see [leaveLockScreen]. */
@Composable
fun LockScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    // The same instance the inner LockScreen gets: both come from this back-stack entry.
    val lockViewModel = metroViewModel<LockViewModel>()
    val canReopenNote = lockViewModel::canReopenNote
    val unlockReveal = LocalUnlockReveal.current
    if (LocalSessionSharedAcrossWindows.current) {
        FollowUnlockFromOtherWindows(navController, lockViewModel, unlockReveal)
    }
    val exits = remember(navController, unlockReveal) {
        LockExits(
            onUnlock = { isSessionLocked ->
                navController.leaveLockScreen(unlockReveal, isSessionLocked, canReopenNote)
            },
            // A recovery-phrase unlock: set a new PIN when none can be used, otherwise choose whether to keep it.
            onRecoveryUnlock = { navController.leaveForRecoveryStage(lockViewModel.recoveryStage()) },
            onVaultLost = {
                navController.navigate(NavRoutes.LossDataRoute.name) {
                    popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
                }
            },
        )
    }
    LockScreen(exits = exits, modifier = modifier)
}

/**
 * In a host with several windows over one vault, an unlock made on another window's lock screen opens the vault
 * for this one too: this lock screen leaves for Home (or the note this window had open) like its own unlock does,
 * without the reveal, or for the new-PIN screen when the other window unlocked with the recovery phrase. A short
 * grace lets this window's own unlock start its reveal first, which this must not cut off.
 */
@Composable
private fun FollowUnlockFromOtherWindows(
    navController: NavHostController,
    viewModel: LockViewModel,
    reveal: UnlockRevealState?,
) {
    LaunchedEffect(viewModel) {
        viewModel.sessionLocked.collectLatest { locked ->
            if (locked) return@collectLatest
            delay(FOLLOW_UNLOCK_GRACE_MS)
            val ownUnlock = viewModel.busy.value || reveal?.running == true
            if (!ownUnlock && !viewModel.isSessionLocked()) {
                val stage = viewModel.recoveryStage()
                if (stage != RecoveryStage.NONE) {
                    // The other window unlocked with the recovery phrase: this one asks the same, new PIN or choice.
                    navController.leaveForRecoveryStage(stage)
                } else {
                    navController.leaveLockScreen(null, viewModel::isSessionLocked, viewModel::canReopenNote)
                }
            }
        }
    }
}

private const val FOLLOW_UNLOCK_GRACE_MS = 250L

/** After a recovery-phrase unlock: the new-PIN screen when a PIN is required, the keep-or-change choice otherwise. */
private fun NavHostController.leaveForRecoveryStage(stage: RecoveryStage) {
    val route = if (stage == RecoveryStage.CHOOSE) NavRoutes.RecoveryChoiceRoute else NavRoutes.PinCodeConfig
    navigate(route.name) {
        popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
    }
}

@Composable
fun LockScreen(exits: LockExits, modifier: Modifier = Modifier, viewModel: LockViewModel = metroViewModel()) {
    val host = LocalBiometricHost.current
    val busy by viewModel.busy.collectAsState()
    // Above the loading view below: the forms' input and errors survive the credential check.
    val form = remember { LockFormState() }

    // From the recovery form Back returns to the PIN pad; from the PIN pad it goes to
    // [LockExits.onBack], or nowhere: by default Back never leaves the lock screen.
    BackHandler(enabled = true) { onLockBack(busy, form, exits.onBack) }

    val biometricEnabled = remember {
        viewModel.biometricEnabled() && viewModel.biometricAvailable()
    }

    fun goHome() = exits.onUnlock(viewModel::isSessionLocked)

    fun goToPinReset() = exits.onRecoveryUnlock(viewModel::isSessionLocked)

    fun onPinKeyLoss() = handlePinKeyLoss(form, viewModel.recoveryAvailable(), biometricEnabled, exits.onVaultLost)

    fun promptBiometric() {
        if (host != null && viewModel.lockoutRemainingMillis() <= 0) {
            viewModel.authenticateBiometric(host) { unlocked ->
                if (unlocked) goHome()
            }
        }
    }

    // Once per lock screen, and only with the window in front: a lock screen that appears while the
    // app is in the background (the auto-lock) would otherwise start a prompt the system cancels
    // unseen. The fingerprint button stays available meanwhile and after.
    LaunchedEffect(host) {
        if (biometricEnabled && host != null) {
            host.owner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            promptBiometric()
        }
    }

    LockForms(
        form = form,
        busy = busy,
        capabilities = LockCapabilities(
            biometricEnabled = biometricEnabled && host != null,
            recoveryAvailable = viewModel.recoveryAvailable(),
        ),
        actions = LockActions(
            checks = LockChecks(
                pin = viewModel::authenticatePin,
                seed = viewModel::authenticateSeed,
            ),
            lockoutRemainingMillis = viewModel::lockoutRemainingMillis,
            onUnlock = ::goHome,
            onRecoveryUnlock = ::goToPinReset,
            onKeyLoss = ::onPinKeyLoss,
            onPromptBiometric = ::promptBiometric,
        ),
        modifier = modifier,
    )
}

private fun onLockBack(busy: Boolean, form: LockFormState, onBack: (() -> Unit)?) {
    when {
        busy -> Unit
        form.useRecovery -> form.back()
        else -> onBack?.invoke()
    }
}

/**
 * Leaves the lock screen for Home, and the note that was open when the app re-locked (see
 * EnclyApp), once the reveal covers the window so Home composes out of sight. If the
 * session closed again while the reveal played (e.g. the app went to the background), it stays
 * on the lock screen rather than open Home over a locked vault. The note is not reopened when
 * [canReopenNote] says the open vault is not the one it was in (a wipe-PIN erase since).
 */
private fun NavHostController.leaveLockScreen(
    reveal: UnlockRevealState?,
    isSessionLocked: () -> Boolean,
    canReopenNote: (savedEpoch: Long?) -> Boolean,
) {
    val lockState = currentBackStackEntry?.savedStateHandle
    val returnRoute = lockState?.get<String>(RelockReturn.RETURN_ROUTE)
    val returnEpoch = lockState?.get<Long>(RelockReturn.RETURN_EPOCH)
    reveal.revealThen {
        if (!isSessionLocked()) {
            navigate(NavRoutes.HomeRoute.name) {
                popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
            }
            // Checked after the unlock: an erase is what changes the epoch.
            if (returnRoute != null && canReopenNote(returnEpoch)) {
                navigate(returnRoute)
                currentBackStackEntry?.savedStateHandle?.set(RelockReturn.PLAIN_ENTRY, true)
            }
        }
    }
}

/**
 * The PIN can no longer unlock on this device. With a recovery phrase the lock screen switches
 * to it (the form shows why); with only a fingerprint the PIN pad keeps the message; with
 * neither, nothing can open the vault here any more ([onVaultLost]).
 */
private fun handlePinKeyLoss(
    form: LockFormState,
    recoveryAvailable: Boolean,
    biometric: Boolean,
    onVaultLost: () -> Unit,
) {
    if (recoveryAvailable) {
        form.useRecovery = true
    } else if (!biometric) {
        onVaultLost()
    }
}
