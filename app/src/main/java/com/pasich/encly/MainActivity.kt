package com.pasich.encly

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.presentation.navigation.AppNavHost
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        setContent {
            val navController = rememberNavController()
            val destination = when (securityManager.securityStatus) {
                InitialStatus.MAIN -> NavRoutes.HomeRoute
                InitialStatus.ONBOARDING -> NavRoutes.OnboardingRoute
                InitialStatus.LOSS_DATABASE -> NavRoutes.LossDataRoute
                InitialStatus.AUTH -> NavRoutes.LockRoute
                InitialStatus.SETUP_AUTH -> NavRoutes.AuthSetupRoute
                InitialStatus.LOSS_CRYPTO -> NavRoutes.LossDataRoute  // TODO: dedicated crypto-loss recovery
                InitialStatus.NO -> return@setContent
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
        // Edge-to-edge is forced on Android 15+/targetSdk 36. Inset the whole app by
        // the status bar once (windowInsetsPadding also consumes it, so Scaffold
        // screens don't double-pad). The transparent status bar then sits over the
        // app-colored windowBackground (see themes.xml), so it blends with the app
        // instead of showing content under it or a black strip.
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
