package com.pasich.encly.presentation.screen

/**
 * Where the lock screen goes next. [onUnlock] (PIN or fingerprint) and [onRecoveryUnlock] (the
 * recovery phrase: the PIN was forgotten) get a check for "the session closed again meanwhile".
 * [onVaultLost]: nothing can open the vault on this device any more. [onBack], when set, is where
 * Back on the PIN pad goes; without it Back never leaves the lock screen.
 */
class LockExits(
    val onUnlock: (isSessionLocked: () -> Boolean) -> Unit,
    val onRecoveryUnlock: (isSessionLocked: () -> Boolean) -> Unit,
    val onVaultLost: () -> Unit,
    val onBack: (() -> Unit)? = null,
)
