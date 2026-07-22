package com.pasich.encly

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.presentation.navigation.AppNavHost
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var sessionLockManager: SessionLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        // Resolve the startup state off the main thread (Keystore/crypto/prefs reads);
        // keep the splash visible until it's ready.
        var initialStatus by mutableStateOf<InitialStatus?>(null)
        splashScreen.setKeepOnScreenCondition { initialStatus == null }

        lifecycleScope.launch {
            initialStatus = withContext(Dispatchers.IO) { securityManager.resolveInitialStatus() }
        }

        setContent {
            val status = initialStatus ?: return@setContent
            val navController = rememberNavController()
            val destination = when (status) {
                InitialStatus.MAIN -> NavRoutes.HomeRoute
                InitialStatus.ONBOARDING -> NavRoutes.OnboardingRoute
                InitialStatus.LOSS_DATABASE -> NavRoutes.LossDataRoute
                InitialStatus.AUTH -> NavRoutes.LockRoute
                InitialStatus.SETUP_AUTH -> NavRoutes.AuthSetupRoute
                // integrity check failed -> recovery (wipe & restart)
                InitialStatus.LOSS_CRYPTO -> NavRoutes.LossDataRoute
                InitialStatus.NO -> return@setContent
            }

            // Auto re-lock: when the app was backgrounded and re-locked, route back to the
            // lock screen. collectAsStateWithLifecycle defers the emission until the app is
            // foregrounded again, so navigation happens on return, not while in the background.
            val locked by sessionLockManager.locked.collectAsStateWithLifecycle()
            LaunchedEffect(locked) {
                if (locked) {
                    navController.navigate(NavRoutes.LockRoute.name) {
                        launchSingleTop = true
                    }
                }
            }

            App(
                navController = navController, startDestination = destination.name
            )
        }
    }
}

@Composable
fun App(
    navController: NavHostController, startDestination: String
) {
    AppTheme {
        // Edge-to-edge is forced on Android 15+/targetSdk 36. Paint the whole window
        // (including behind the transparent status bar) with the same Compose
        // background the screens use, then inset the content by the status bar so
        // content sits below the bar with no colour seam. windowInsetsPadding also
        // consumes the inset so Scaffold screens don't double-pad.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                AppNavHost(
                    navController = navController, startDestination = startDestination
                )
            }
        }
    }
}
