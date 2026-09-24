package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.pasich.encly.presentation.effects.animationScreens
import com.pasich.encly.presentation.screen.AboutScreen
import com.pasich.encly.presentation.screen.EditTagScreen
import com.pasich.encly.presentation.screen.FaqScreen
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.LossReason
import com.pasich.encly.presentation.screen.LossRecoveryScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.SupportScreen
import com.pasich.encly.presentation.screen.TasksScreen
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.screen.onboarding.OnboardingScreen
import com.pasich.encly.presentation.screen.settings.AppearanceScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.screen.settings.SettingsScreen
import com.pasich.encly.presentation.screen.trash.TrashScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
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

        animationScreens(NavRoutes.AppearanceRoute.name) {
            AppearanceScreen(navController)
        }

        animationScreens(NavRoutes.BackupRoute.name) {
            BackupScreen(navController)
        }

        animationScreens(NavRoutes.OnboardingRoute.name) {
            // Onboarding includes the mandatory PIN setup; it completes only once the vault
            // is committed and open.
            OnboardingScreen(onComplete = {
                navController.navigate(NavRoutes.HomeRoute.name) {
                    popUpTo(NavRoutes.OnboardingRoute.name) { inclusive = true }
                }
            })
        }

        animationScreens(NavRoutes.LossDataRoute.name) {
            LossRoute(navController, NavRoutes.LossDataRoute, LossReason.DAMAGED)
        }
        animationScreens(NavRoutes.LegacyVaultRoute.name) {
            LossRoute(navController, NavRoutes.LegacyVaultRoute, LossReason.OLDER_VERSION)
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
        ) { entry ->
            EditNoteDestination(navController, entry)
        }
    }
}

/**
 * The note editor. Records the id of its note on [entry] (also the id a new note gets on its
 * first save), so a background re-lock can return to it (see [RelockReturn]).
 */
@Composable
private fun EditNoteDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val noteState by viewModel.state.collectAsState()
    LaunchedEffect(entry, noteState.note.id) {
        entry.savedStateHandle[RelockReturn.OPEN_NOTE_ID] = noteState.note.id
    }
    // The screen resolves the same ViewModel instance: it is scoped to [entry].
    EditNoteScreen(navController)
}

/** The vault cannot be opened ([reason]): wiping it, once confirmed, starts onboarding afresh. */
@Composable
private fun LossRoute(
    navController: NavHostController,
    route: NavRoutes,
    reason: LossReason,
    recoveryViewModel: LossRecoveryViewModel = hiltViewModel(),
) {
    LossRecoveryScreen(
        reason = reason,
        onRecoveryConfirm = {
            recoveryViewModel.wipeAllData()
            navController.navigate(NavRoutes.OnboardingRoute.name) {
                popUpTo(route.name) { inclusive = true }
            }
        },
    )
}
