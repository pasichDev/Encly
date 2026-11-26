package com.pasich.encly.presentation.navigation

import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.presentation.designsystem.AppCommand

/**
 * Whether the keyboard may run [command]: with the vault open, and, while the recovery phrase's unlock still owes a
 * new PIN (or the choice to keep the old one), only the lock: nothing else may leave that screen.
 */
internal fun commandAllowed(command: AppCommand, vaultOpen: Boolean, recovery: RecoveryStage): Boolean =
    vaultOpen && (recovery == RecoveryStage.NONE || command == AppCommand.LockApp)

/**
 * Where the app starts for [status]; null while there is nothing to show yet. A start with the vault already open
 * (a second window of the desktop, a recreated activity) still owes what the recovery phrase's unlock left to
 * decide ([recovery]): going to Home would skip the new PIN, or the choice to keep the old one.
 */
internal fun startRoute(status: InitialStatus, recovery: RecoveryStage = RecoveryStage.NONE): NavRoutes? =
    when (status) {
        InitialStatus.MAIN -> when (recovery) {
            RecoveryStage.NONE -> NavRoutes.HomeRoute
            RecoveryStage.CHOOSE -> NavRoutes.RecoveryChoiceRoute
            RecoveryStage.MUST_SET_PIN -> NavRoutes.PinCodeConfig
        }

        // PIN setup is part of onboarding, which restarts until the vault is committed.
        InitialStatus.ONBOARDING, InitialStatus.SETUP_AUTH -> NavRoutes.OnboardingRoute

        InitialStatus.LOSS_DATABASE, InitialStatus.LOSS_CRYPTO -> NavRoutes.LossDataRoute

        InitialStatus.AUTH -> NavRoutes.LockRoute

        InitialStatus.LEGACY_VAULT -> NavRoutes.LegacyVaultRoute

        InitialStatus.STORAGE_UNREADABLE -> NavRoutes.StorageUnreadableRoute

        InitialStatus.NO -> null
    }
