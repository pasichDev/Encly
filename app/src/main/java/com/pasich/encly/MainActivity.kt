package com.pasich.encly

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.presentation.components.SecureTextInputBoundary
import com.pasich.encly.presentation.components.SensitiveClipboardBoundary
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.presentation.effects.UnlockRevealOverlay
import com.pasich.encly.presentation.effects.UnlockRevealState
import com.pasich.encly.presentation.navigation.AppNavHost
import com.pasich.encly.presentation.navigation.LocalEraseEpoch
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.navigation.SessionResume
import com.pasich.encly.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * AppCompatActivity (a FragmentActivity, as BiometricPrompt needs) so the in-app language set
 * through AppCompatDelegate.setApplicationLocales is applied on every API level, not only on
 * Android 13+ where the framework does it.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var sessionLockManager: SessionLockManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var keyboardPrivacy: KeyboardPrivacy

    override fun onCreate(savedInstanceState: Bundle?) {
        // Encly always renders protected content. Apply FLAG_SECURE before the splash/content
        // lifecycle starts so screenshots, screen recording, casting and recents snapshots
        // cannot capture an unprotected first frame.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Before NavHost reads the intent: no caller picks a screen through nav deep-link extras.
        stripNavigationExtras(intent)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // After super.onCreate: touching decorView earlier builds the window with the splash
        // theme, which has an action bar, instead of Theme.Encly.
        protectWindow()
        enableEdgeToEdge()
        supportActionBar?.hide()

        // Resolve the startup state and read the theme off the main thread (Keystore/crypto/
        // prefs reads); keep the splash visible until both are ready, so the first frame is
        // drawn in the stored palette, mode and fonts rather than the defaults.
        var initialStatus by mutableStateOf<InitialStatus?>(null)
        splashScreen.setKeepOnScreenCondition { initialStatus == null }

        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                // Never throws: unreadable vault state routes to the damaged-vault screen.
                val status = securityManager.resolveInitialStatus()
                try {
                    settingsRepository.loadThemeSettings(existingInstall = !securityManager.isOnboardingShow())
                } catch (_: IOException) {
                    // An unreadable settings file: the theme starts from its defaults.
                }
                status
            }
            // A committed vault that is not open starts locked, before the first frame, so a back
            // stack restored after process death is replaced by the lock screen, never shown.
            if (status == InitialStatus.AUTH) sessionLockManager.requireUnlock()
            initialStatus = status
        }

        setContent {
            val destination = initialStatus?.let(::startRoute) ?: return@setContent
            val navController = rememberNavController()

            // Auto re-lock. Collected without a lifecycle gate so the value is already true in
            // the first frame after returning from the background. That frame still composes
            // the pre-lock destination (e.g. an open note), so the shield below covers the
            // NavHost until the lock screen is the only visible entry. Navigation itself runs
            // after the frame, from this effect.
            val locked by sessionLockManager.locked.collectAsState()
            val strictKeyboard by keyboardPrivacy.strict.collectAsState()
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
                shielded = shielded,
                locked = locked,
                strictKeyboard = strictKeyboard,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        stripNavigationExtras(intent)
        super.onNewIntent(intent)
    }
}

/**
 * Window-level privacy: no autofill service sees any field (notes are not form data), the
 * content is marked sensitive for accessibility services that are not accessibility tools
 * (TalkBack still reads it), and other apps' overlays are hidden while Encly is in front.
 * Every activity calls it after super.onCreate, next to FLAG_SECURE set before it.
 */
internal fun Activity.protectWindow() {
    window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        window.decorView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window.setHideOverlayWindows(true)
    }
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
    val view = LocalView.current
    val currentIsDatabaseUnlocked by rememberUpdatedState(isDatabaseUnlocked)
    DisposableEffect(navController) {
        val guard = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (isProtectedRoute(destination.route) && !currentIsDatabaseUnlocked()) {
                view.post { navController.showLockScreen() }
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

/** Navigation's own deep-link extras; the exported launcher activity must not honour them. */
private const val NAV_EXTRA_PREFIX = "android-support-nav:controller:"

private fun stripNavigationExtras(intent: Intent?) {
    val extras = intent?.extras ?: return
    extras.keySet().filter { it.startsWith(NAV_EXTRA_PREFIX) }.forEach(intent::removeExtra)
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
            readOnly = entry.arguments?.getBoolean(EDIT_NOTE_READ_ONLY_ARG) == true,
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
    strictKeyboard: Boolean = false,
) {
    AppTheme {
        SecureTextInputBoundary(strict = strictKeyboard) {
            SensitiveClipboardBoundary {
                // Edge-to-edge is forced on Android 15+/targetSdk 36. Paint the whole window
                // (including behind the transparent status bar) with the same Compose
                // background the screens use, then inset the content by the status bar so
                // content sits below the bar with no colour seam. windowInsetsPadding also
                // consumes the inset so Scaffold screens don't double-pad.
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.statusBars),
                            ) {
                                CompositionLocalProvider(LocalEraseEpoch provides eraseEpoch) {
                                    AppNavHost(
                                        navController = navController,
                                        startDestination = startDestination,
                                    )
                                }
                            }
                        }
                    }
                    UnlockRevealOverlay(unlockReveal)
                    if (shielded) LockShield()
                }
            }
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
