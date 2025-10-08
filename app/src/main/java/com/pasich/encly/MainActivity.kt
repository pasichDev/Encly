package com.pasich.encly

import android.content.Intent
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
import com.pasich.encly.utils.AppUpdateHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // Помощник для обновления приложения
    private lateinit var appUpdateHelper: AppUpdateHelper

    @Inject
    lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        // Проверяем наличие обновлений
        appUpdateHelper = AppUpdateHelper(this, this)
        appUpdateHelper.checkForUpdates()



        setContent {
            val navController = rememberNavController()
            val destination = when (securityManager.securityStatus) {
                InitialStatus.MAIN -> NavRoutes.HomeRoute
                InitialStatus.ONBOARDING -> NavRoutes.OnboardingRoute
                InitialStatus.LOSS_DATABASE -> NavRoutes.LossDataRoute
                InitialStatus.AUTH -> NavRoutes.HomeRoute // TODO
                InitialStatus.LOSS_CRYPTO -> NavRoutes.LossDataRoute  // TODO
                InitialStatus.NO -> return@setContent
            }

            App(
                navController = navController, startDestination = destination.name
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Проверяем статус обновления
        appUpdateHelper.checkUpdateStatus()

    }

    override fun onPause() {
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppUpdateHelper.UPDATE_REQUEST_CODE) {
            appUpdateHelper.processUpdateResult(resultCode)
        }
    }

    override fun onDestroy() {
        appUpdateHelper.cleanup()
        super.onDestroy()
    }
}

@Composable
fun App(
    navController: NavHostController, startDestination: String
) {
    AppTheme {
        AppNavHost(
            navController = navController, startDestination = startDestination
        )
    }
}
