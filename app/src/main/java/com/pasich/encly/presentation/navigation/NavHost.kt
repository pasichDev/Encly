package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.pasich.encly.presentation.effects.animationScreens
import com.pasich.encly.presentation.screen.AboutScreen
import com.pasich.encly.presentation.screen.AuthSetupScreen
import com.pasich.encly.presentation.screen.EditTagScreen
import com.pasich.encly.presentation.screen.FaqScreen
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.LossRecoveryScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.SupportScreen
import com.pasich.encly.presentation.screen.TasksScreen
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.screen.onboarding.OnboardingScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.screen.settings.SettingsScreen
import com.pasich.encly.presentation.screen.trash.TrashScreen
import com.pasich.encly.presentation.viewmodel.LossRecoveryViewModel

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String = NavRoutes.HomeRoute.name) {
    NavHost(navController, startDestination = startDestination) {
        animationScreens(NavRoutes.HomeRoute.name) {
            MainRootScreen(
                navController,
            )
        }
        animationScreens(NavRoutes.TrashRoute.name) {
            TrashScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.SettingsRoute.name) {
            SettingsScreen(
                navController,
            )
        }
        animationScreens(NavRoutes.EditTagRoute.name) {
            EditTagScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.SupportRoute.name) {
            SupportScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.TasksRoute.name) {
            TasksScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.AboutRoute.name) {
            AboutScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.FaqRoute.name) {
            FaqScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.SecuritySettingsRoute.name) {
            SecuritySettingsScreen(
                navController,
            )
        }

        animationScreens(NavRoutes.BackupRoute.name) {
            BackupScreen(navController)
        }

        animationScreens(NavRoutes.OnboardingRoute.name) {
            OnboardingScreen(onComplete = {
                // PIN + biometric setup is mandatory before entering the app.
                navController.navigate(NavRoutes.AuthSetupRoute.name) {
                    popUpTo(NavRoutes.OnboardingRoute.name) { inclusive = true }
                }
            })
        }

        animationScreens(NavRoutes.AuthSetupRoute.name) {
            AuthSetupScreen(navController)
        }

        animationScreens(NavRoutes.LossDataRoute.name) {
            val recoveryViewModel: LossRecoveryViewModel = hiltViewModel()
            LossRecoveryScreen(onRecoveryConfirm = {
                recoveryViewModel.wipeAllData()
                navController.navigate(NavRoutes.OnboardingRoute.name) {
                    popUpTo(NavRoutes.LossDataRoute.name) { inclusive = true }
                }
            })
        }
        animationScreens(NavRoutes.PinCodeConfig.name) {
            PinCodeConfigScreen(navController)
        }

        animationScreens(NavRoutes.LockRoute.name) {
            LockScreen(navController)
        }

        animationScreens(
            route = "${NavRoutes.EditNoteRoute.name}/{idNote}" +
                "?isReadTrashOnly={isReadTrashOnly}&copySource={copySource}&addTag={addTag}",
            arguments = listOf(
                navArgument("idNote") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("isReadTrashOnly") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("copySource") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("addTag") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            EditNoteScreen(navController)
        }
    }
}
