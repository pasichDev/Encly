package com.pasich.encly

import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.presentation.components.SecureTextInputBoundary
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.presentation.effects.UnlockRevealOverlay
import com.pasich.encly.presentation.effects.UnlockRevealState
import com.pasich.encly.presentation.navigation.AppNavHost
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Encly always renders protected content. Apply FLAG_SECURE before the splash/content
        // lifecycle starts so screenshots, screen recording, casting and recents snapshots
        // cannot capture an unprotected first frame.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supportActionBar?.hide()

        // Resolve the startup state and read the theme off the main thread (Keystore/crypto/
        // prefs reads); keep the splash visible until both are ready, so the first frame is
        // drawn in the stored palette, mode and fonts rather than the defaults.
        var initialStatus by mutableStateOf<InitialStatus?>(null)
        splashScreen.setKeepOnScreenCondition { initialStatus == null }

        lifecycleScope.launch {
            initialStatus = withContext(Dispatchers.IO) {
                val status = securityManager.resolveInitialStatus()
                try {
                    settingsRepository.loadThemeSettings(existingInstall = !securityManager.isOnboardingShow())
                } catch (_: IOException) {
                    // An unreadable settings file: the theme starts from its defaults.
                }
                status
            }
        }

        setContent {
            val status = initialStatus ?: return@setContent
            val navController = rememberNavController()
            val destination = when (status) {
                InitialStatus.MAIN -> NavRoutes.HomeRoute

                // PIN setup is part of onboarding, which restarts until the vault is committed.
                InitialStatus.ONBOARDING, InitialStatus.SETUP_AUTH -> NavRoutes.OnboardingRoute

                InitialStatus.LOSS_DATABASE -> NavRoutes.LossDataRoute

                InitialStatus.AUTH -> NavRoutes.LockRoute

                InitialStatus.LOSS_CRYPTO -> NavRoutes.LossDataRoute

                InitialStatus.LEGACY_VAULT -> NavRoutes.LegacyVaultRoute

                InitialStatus.NO -> return@setContent
            }

            // Auto re-lock. Collected without a lifecycle gate so the value is already true in
            // the first frame after returning from the background. That frame still composes
            // the pre-lock destination (e.g. an open note), so the shield below covers the
            // NavHost until the lock screen is the only visible entry. Navigation itself runs
            // after the frame, from this effect.
            val locked by sessionLockManager.locked.collectAsState()
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
                shielded = shielded,
            )
        }
    }
}

/** The editor's "opened from the trash, read-only" navigation argument (see AppNavHost). */
private const val EDIT_NOTE_READ_ONLY_ARG = "isReadTrashOnly"

/**
 * Replaces every screen with the lock screen after a background re-lock, remembering the note
 * that was open (see [RelockReturn]) so the unlock can return to it.
 */
private fun NavHostController.showLockScreen() {
    val returnRoute = currentBackStackEntry?.let { entry ->
        RelockReturn.routeFor(
            destinationRoute = entry.destination.route,
            openNoteId = entry.savedStateHandle.get<Long>(RelockReturn.OPEN_NOTE_ID),
            readOnly = entry.arguments?.getBoolean(EDIT_NOTE_READ_ONLY_ARG) == true,
        )
    }
    navigate(NavRoutes.LockRoute.name) {
        // Drop every screen/ViewModel backed by the now-closed Room instance.
        // Unlock starts a fresh Home graph with fresh DAO flows.
        popUpTo(graph.id) {
            inclusive = false
        }
        launchSingleTop = true
    }
    if (returnRoute != null) currentBackStackEntry?.savedStateHandle?.set(RelockReturn.RETURN_ROUTE, returnRoute)
}

@Composable
fun App(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    shielded: Boolean = false,
) {
    AppTheme {
        SecureTextInputBoundary {
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
