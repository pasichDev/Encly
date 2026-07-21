package com.pasich.encly

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
        // Edge-to-edge (forced on Android 15+/targetSdk 36): the status bar stays
        // transparent and the app draws behind it. Each screen insets its own top
        // content (Scaffold screens do this automatically; others use statusBarsPadding).
        AppNavHost(
            navController = navController, startDestination = startDestination
        )
    }
}
