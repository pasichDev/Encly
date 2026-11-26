package com.pasich.encly.presentation.screen.pincode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.pasich.encly.presentation.navigation.LocalVaultLocked

/**
 * For a screen that sits under the lock shield while the vault is locked: runs [onLock] as soon as it is, so what
 * the screen holds of a passphrase is dropped then and not when navigation gets round to disposing it, and says
 * whether it is, so the screen can refuse input meanwhile.
 */
@Composable
internal fun clearWhenVaultLocks(onLock: () -> Unit): Boolean {
    val locked = LocalVaultLocked.current
    val current by rememberUpdatedState(onLock)
    LaunchedEffect(locked) { if (locked) current() }
    return locked
}
