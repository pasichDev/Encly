package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/**
 * SecurityManager.isDecoySession, which MainActivity provides. It is a function, not a value:
 * the vault can differ from one unlock to the next, so it is asked each time a screen composes
 * and never kept. Without it every session is the real vault.
 */
val LocalDecoySession = staticCompositionLocalOf<() -> Boolean> { { false } }

/** Whether the vault that is open now is the decoy one; screens ask this while composing. */
@Composable
fun isDecoySession(): Boolean = LocalDecoySession.current()

/**
 * Screens that only make sense in the real vault (backup, PIN settings). In a decoy session they
 * never compose: the user is sent to Settings instead, where those entries are not listed, so
 * nothing on the way hints that another vault exists.
 */
@Composable
internal fun RealVaultOnly(navController: NavHostController, content: @Composable () -> Unit) {
    if (isDecoySession()) {
        LaunchedEffect(Unit) {
            navController.navigate(NavRoutes.SettingsRoute.name) {
                popUpTo(NavRoutes.SettingsRoute.name) { inclusive = true }
                launchSingleTop = true
            }
        }
    } else {
        content()
    }
}
