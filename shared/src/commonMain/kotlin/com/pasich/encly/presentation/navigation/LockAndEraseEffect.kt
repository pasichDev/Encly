package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.pasich.encly.core.security.SessionLockManager
import kotlinx.coroutines.flow.first

/**
 * What a window does when the vault locks ([onLock]: the lock screen) or is erased in any window ("Erase all data"):
 * every window then drops what it shows and starts over with onboarding, not only the one that erased. Runs once the
 * NavHost has set its graph: navigating before it throws ("You must call setGraph() before calling getGraph()"), which
 * an effect that runs at once (a busy machine, a test's dispatcher) would do in the first frame of a vault that starts
 * locked.
 */
@Composable
internal fun LockAndEraseEffect(
    navController: NavHostController,
    sessionLockManager: SessionLockManager,
    locked: Boolean,
    onLock: () -> Unit,
) {
    val currentOnLock by rememberUpdatedState(onLock)
    val erasedCount by sessionLockManager.erasedCount.collectAsState()
    var handledErase by remember { mutableLongStateOf(erasedCount) }
    LaunchedEffect(locked, erasedCount) {
        navController.currentBackStackEntryFlow.first()
        when {
            erasedCount != handledErase -> {
                handledErase = erasedCount
                navController.startOverAfterErase()
            }

            locked -> currentOnLock()
        }
    }
}

/** Onboarding, over nothing: the vault was erased, so no screen of it stays. Not again when already there. */
private fun NavHostController.startOverAfterErase() {
    if (currentDestination?.route == NavRoutes.OnboardingRoute.name) return
    navigate(NavRoutes.OnboardingRoute.name) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
