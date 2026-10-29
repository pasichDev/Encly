package com.pasich.encly.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.presentation.effects.UnlockRevealOverlay
import com.pasich.encly.presentation.effects.UnlockRevealState
import com.pasich.encly.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * The whole app UI: the theme, the navigation host and the guards that keep protected screens away from
 * a locked vault. The host (an Activity on Android) resolves the vault's [startStatus] first and draws
 * nothing, or its own splash, while it is null; it also wraps this in whatever the platform needs around
 * every text field (the keyboard and clipboard policy) and provides the `Local...` services.
 */
@Composable
fun EnclyApp(
    startStatus: InitialStatus?,
    securityManager: SecurityManager,
    sessionLockManager: SessionLockManager,
    modifier: Modifier = Modifier,
) {
    val destination = startStatus?.let(::startRoute) ?: return
    val navController = rememberNavController()

    // Auto re-lock. Collected without a lifecycle gate so the value is already true in
    // the first frame after returning from the background. That frame still composes
    // the pre-lock destination (e.g. an open note), so the shield below covers the
    // NavHost until the lock screen is the only visible entry. Navigation itself runs
    // after the frame, from this effect.
    val locked by sessionLockManager.locked.collectAsState()
    LockRouteGuard(navController, securityManager::isDatabaseUnlocked)
    SessionResumeGuard(navController, sessionLockManager)
    val visibleEntries by navController.visibleEntries.collectAsState()
    val shielded = locked && visibleEntries.any {
        it.destination.route != NavRoutes.LockRoute.name
    }
    LaunchedEffect(locked) {
        if (locked) navController.showLockScreen()
    }

    App(
        navController = navController,
        startDestination = destination.name,
        eraseEpoch = securityManager::eraseEpoch,
        modifier = modifier,
        shielded = shielded,
        locked = locked,
    )
}

/** Where the app starts for [status]; null while there is nothing to show yet. */
private fun startRoute(status: InitialStatus): NavRoutes? = when (status) {
    InitialStatus.MAIN -> NavRoutes.HomeRoute

    // PIN setup is part of onboarding, which restarts until the vault is committed.
    InitialStatus.ONBOARDING, InitialStatus.SETUP_AUTH -> NavRoutes.OnboardingRoute

    InitialStatus.LOSS_DATABASE, InitialStatus.LOSS_CRYPTO -> NavRoutes.LossDataRoute

    InitialStatus.AUTH -> NavRoutes.LockRoute

    InitialStatus.LEGACY_VAULT -> NavRoutes.LegacyVaultRoute

    InitialStatus.NO -> null
}

/**
 * Central route guard: whatever tries to show a vault screen while the database is closed (a
 * back stack restored after process death, a stale navigation) is sent to the lock screen.
 * Navigating from inside the listener is deferred to the next main-thread turn.
 */
@Composable
private fun LockRouteGuard(navController: NavHostController, isDatabaseUnlocked: () -> Boolean) {
    val scope = rememberCoroutineScope()
    val currentIsDatabaseUnlocked by rememberUpdatedState(isDatabaseUnlocked)
    DisposableEffect(navController) {
        val guard = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (isProtectedRoute(destination.route) && !currentIsDatabaseUnlocked()) {
                scope.launch {
                    // The next turn of the main thread, whatever dispatcher runs the composition's scope.
                    yield()
                    navController.showLockScreen()
                }
            }
        }
        navController.addOnDestinationChangedListener(guard)
        onDispose { navController.removeOnDestinationChangedListener(guard) }
    }
}

/**
 * Coming back to the foreground after the vault was unlocked on another lock screen (see
 * [SessionResume.afterStop]) rebuilds the back stack instead of showing screens of a session
 * that ended. Runs on ON_START itself, before the first frame and the re-lock effect.
 */
@Composable
private fun SessionResumeGuard(navController: NavHostController, sessionLockManager: SessionLockManager) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        var stoppedAt: Long? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = sessionLockManager.sessionGeneration

                Lifecycle.Event.ON_START -> {
                    val resume = SessionResume.afterStop(
                        stoppedAt = stoppedAt,
                        generation = sessionLockManager.sessionGeneration,
                        locked = sessionLockManager.locked.value,
                    )
                    stoppedAt = null
                    when (resume) {
                        SessionResume.KEEP -> Unit

                        SessionResume.LOCK_SCREEN -> navController.showLockScreen(keepNote = false)

                        SessionResume.HOME -> navController.navigate(NavRoutes.HomeRoute.name) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Screens that are reachable without an open vault; every other route shows vault content. */
private val PUBLIC_ROUTES = setOf(
    NavRoutes.LockRoute.name,
    NavRoutes.OnboardingRoute.name,
    NavRoutes.LossDataRoute.name,
    NavRoutes.LegacyVaultRoute.name,
)

private fun isProtectedRoute(route: String?): Boolean = route != null && route !in PUBLIC_ROUTES

/** The editor's "opened from the trash, read-only" navigation argument (see AppNavHost). */
private const val EDIT_NOTE_READ_ONLY_ARG = "isReadTrashOnly"

/**
 * Replaces every screen with the lock screen after a background re-lock, remembering the note
 * that was open (see [RelockReturn]) with the erase epoch of the vault it was opened in, so the
 * unlock returns to it only in that vault. Without [keepNote], or from any other screen,
 * whatever was remembered before is forgotten.
 */
private fun NavHostController.showLockScreen(keepNote: Boolean = true) {
    val editor = currentBackStackEntry?.takeIf { keepNote }
    val returnRoute = editor?.let { entry ->
        RelockReturn.routeFor(
            destinationRoute = entry.destination.route,
            openNoteId = entry.savedStateHandle.get<Long>(RelockReturn.OPEN_NOTE_ID),
            readOnly = entry.arguments?.read { getBooleanOrNull(EDIT_NOTE_READ_ONLY_ARG) } == true,
        )
    }
    val returnEpoch = editor?.savedStateHandle?.get<Long>(RelockReturn.OPEN_NOTE_EPOCH)
    navigate(NavRoutes.LockRoute.name) {
        // Drop every screen/ViewModel backed by the now-closed Room instance.
        // Unlock starts a fresh Home graph with fresh DAO flows.
        popUpTo(graph.id) {
            inclusive = false
        }
        launchSingleTop = true
    }
    val lockState = currentBackStackEntry?.savedStateHandle ?: return
    if (returnRoute != null && returnEpoch != null) {
        lockState[RelockReturn.RETURN_ROUTE] = returnRoute
        lockState[RelockReturn.RETURN_EPOCH] = returnEpoch
    } else {
        lockState.remove<String>(RelockReturn.RETURN_ROUTE)
        lockState.remove<Long>(RelockReturn.RETURN_EPOCH)
    }
}

@Composable
fun App(
    navController: NavHostController,
    startDestination: String,
    eraseEpoch: () -> Long,
    modifier: Modifier = Modifier,
    shielded: Boolean = false,
    locked: Boolean = false,
) {
    AppTheme {
        // Edge-to-edge is forced on Android 15+/targetSdk 36. The window is painted with
        // the same Compose background the screens use; every screen draws under the
        // transparent status bar and pads its own top inset (its top bar does), so it
        // paints that band itself and the band moves with the screen in a transition.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val unlockReveal = remember { UnlockRevealState() }
            // A re-lock during the reveal ends it: no deferred navigation into the vault,
            // no reveal left covering the lock screen.
            LaunchedEffect(locked) { if (locked) unlockReveal.cancel() }
            CompositionLocalProvider(LocalUnlockReveal provides unlockReveal) {
                // The whole app layer moves with the unlock reveal: it slides in from the
                // right and pushes the logo colour out (see UnlockRevealOverlay).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = unlockReveal.contentOffset * size.width }
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    CompositionLocalProvider(LocalEraseEpoch provides eraseEpoch) {
                        AppNavHost(
                            navController = navController,
                            startDestination = startDestination,
                        )
                    }
                }
            }
            UnlockRevealOverlay(unlockReveal)
            if (shielded) LockShield()
        }
    }
}

/**
 * Opaque, input-swallowing cover drawn above the NavHost while a re-locked session is still
 * composing protected screens. Plaintext from before the lock is never drawn or tappable.
 */
@Composable
private fun LockShield() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    )
}
