package com.pasich.encly.presentation.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.presentation.effects.LocalSharedTransitionScope
import com.pasich.encly.presentation.effects.animationScreens
import com.pasich.encly.presentation.effects.enterFor
import com.pasich.encly.presentation.effects.exitFor
import com.pasich.encly.presentation.pane.CenteredContent
import com.pasich.encly.presentation.pane.NavigationPage
import com.pasich.encly.presentation.screen.AboutScreen
import com.pasich.encly.presentation.screen.EditTagScreen
import com.pasich.encly.presentation.screen.FaqScreen
import com.pasich.encly.presentation.screen.LicensesScreen
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.LossReason
import com.pasich.encly.presentation.screen.LossRecoveryScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.SupportScreen
import com.pasich.encly.presentation.screen.TasksScreen
import com.pasich.encly.presentation.screen.archive.ArchiveScreen
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.screen.importnotes.ImportNotesScreen
import com.pasich.encly.presentation.screen.onboarding.OnboardingScreen
import com.pasich.encly.presentation.screen.settings.AppearanceScreen
import com.pasich.encly.presentation.screen.settings.DuressPinScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.screen.settings.SettingsScreen
import com.pasich.encly.presentation.screen.settings.WipePinScreen
import com.pasich.encly.presentation.screen.trash.TrashScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.LossRecoveryViewModel
import com.pasich.encly.presentation.viewmodel.NoteTabsViewModel
import com.pasich.encly.ui.theme.LocalWidthClass
import com.pasich.encly.ui.theme.WidthClass
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String = NavRoutes.HomeRoute.name) {
    val donationsEnabled = LocalAppInfo.current.donationsEnabled
    // The scope lets a note card and the editor share their bounds (see rememberNoteContainerModifier).
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController,
                startDestination = startDestination,
                // The back gesture plays the same motion as a tap on Back (shared axis, a fade for the
                // editor, none for the lock screen). Navigation's own default for the gesture (since 2.10)
                // shrinks the leaving screen into the middle of the window.
                predictivePopEnterTransition = {
                    enterFor(initialState.destination.route, targetState.destination.route, forward = false)
                },
                predictivePopExitTransition = {
                    exitFor(initialState.destination.route, targetState.destination.route, forward = false)
                },
            ) {
                animationScreens(NavRoutes.HomeRoute.name) {
                    MainRootScreen(
                        navController,
                    )
                }
                animationScreens(NavRoutes.ArchiveRoute.name) {
                    NavigationPage(navController, NavRoutes.ArchiveRoute) { ArchiveScreen(navController) }
                }
                animationScreens(NavRoutes.TrashRoute.name) {
                    NavigationPage(navController, NavRoutes.TrashRoute) {
                        TrashScreen(
                            navController,
                        )
                    }
                }

                animationScreens(NavRoutes.SettingsRoute.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) {
                        SettingsScreen(
                            navController,
                        )
                    }
                }
                animationScreens(NavRoutes.EditTagRoute.name) {
                    NavigationPage(navController, NavRoutes.EditTagRoute) {
                        EditTagScreen(
                            navController,
                        )
                    }
                }

                // Donations are an F-Droid build feature; the Play build has no route to them.
                if (donationsEnabled) {
                    animationScreens(NavRoutes.SupportRoute.name) {
                        NavigationPage(navController, NavRoutes.SupportRoute) { SupportScreen(navController) }
                    }
                }

                animationScreens(
                    route = "${NavRoutes.TasksRoute.name}?$TASKS_ADD_ARG={$TASKS_ADD_ARG}",
                    arguments = listOf(
                        navArgument(TASKS_ADD_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    NavigationPage(navController, NavRoutes.TasksRoute) {
                        TasksScreen(
                            navController,
                            openNewTask =
                            entry.arguments?.read { getBooleanOrNull(TASKS_ADD_ARG) } == true,
                        )
                    }
                }

                animationScreens(NavRoutes.AboutRoute.name) {
                    NavigationPage(navController, NavRoutes.AboutRoute) {
                        AboutScreen(
                            navController,
                        )
                    }
                }

                animationScreens(NavRoutes.LicensesRoute.name) {
                    NavigationPage(navController, NavRoutes.AboutRoute) { LicensesScreen(navController) }
                }

                animationScreens(NavRoutes.FaqRoute.name) {
                    NavigationPage(navController, NavRoutes.FaqRoute) {
                        FaqScreen(
                            navController,
                        )
                    }
                }

                animationScreens(NavRoutes.SecuritySettingsRoute.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) {
                        SecuritySettingsScreen(
                            navController,
                        )
                    }
                }

                animationScreens(NavRoutes.AppearanceRoute.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) { AppearanceScreen(navController) }
                }

                animationScreens(NavRoutes.BackupRoute.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) { BackupScreen(navController) }
                }

                animationScreens(NavRoutes.ImportNotesRoute.name) {
                    NavigationPage(navController, NavRoutes.ImportNotesRoute) { ImportNotesScreen(navController) }
                }

                animationScreens(NavRoutes.OnboardingRoute.name) {
                    // Onboarding includes the mandatory PIN setup; it completes only once the vault
                    // is committed and open.
                    CenteredContent {
                        OnboardingScreen(onComplete = {
                            navController.navigate(NavRoutes.HomeRoute.name) {
                                popUpTo(NavRoutes.OnboardingRoute.name) { inclusive = true }
                            }
                        })
                    }
                }

                animationScreens(NavRoutes.LossDataRoute.name) {
                    CenteredContent { LossRoute(navController, NavRoutes.LossDataRoute, LossReason.DAMAGED) }
                }
                animationScreens(NavRoutes.LegacyVaultRoute.name) {
                    CenteredContent { LossRoute(navController, NavRoutes.LegacyVaultRoute, LossReason.OLDER_VERSION) }
                }
                animationScreens(NavRoutes.PinCodeConfig.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) { PinCodeConfigScreen(navController) }
                }

                animationScreens(NavRoutes.WipePinRoute.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) { WipePinScreen(navController) }
                }

                animationScreens(NavRoutes.DuressPinRoute.name) {
                    NavigationPage(navController, NavRoutes.SettingsRoute) { DuressPinScreen(navController) }
                }

                animationScreens(NavRoutes.LockRoute.name) {
                    CenteredContent { LockScreen(navController) }
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
                    if (opensInPane(navController, entry)) {
                        OpenInPane(navController, entry)
                    } else {
                        EditNoteDestination(navController, entry)
                    }
                }
            }
        }
    }
}

/**
 * Whether the editor route [entry] belongs in the home screen's detail pane instead of the full screen: the
 * window is expanded, the note is editable and home is right under it (the notes list opens notes in the pane
 * itself; this catches the one that comes from the lock screen, which reopens the note that was open).
 */
@Composable
private fun opensInPane(navController: NavHostController, entry: NavBackStackEntry): Boolean {
    if (LocalWidthClass.current != WidthClass.Expanded) return false
    val readOnly = entry.arguments?.read { getBooleanOrNull("isReadTrashOnly") } == true
    return !readOnly && navController.previousBackStackEntry?.destination?.route == NavRoutes.HomeRoute.name
}

/** Hands the note of [entry] to the detail pane's tabs and takes the editor route off the back stack again. */
@Composable
private fun OpenInPane(navController: NavHostController, entry: NavBackStackEntry) {
    val eraseEpoch = LocalEraseEpoch.current
    // The tabs live in the home entry's ViewModel store, which is the entry right under the editor's.
    val home = remember(entry) { checkNotNull(navController.previousBackStackEntry) }
    val tabsViewModel: NoteTabsViewModel = metroViewModel(home)
    LaunchedEffect(entry) {
        val arguments = entry.arguments
        val noteId = arguments?.read { getLongOrNull("idNote") } ?: -1L
        val copySource = arguments?.read { getLongOrNull("copySource") } ?: -1L
        val addTag = arguments?.read { getLongOrNull("addTag") } ?: 0L
        val params = when {
            copySource > 0L -> "?copySource=$copySource"
            addTag > 0L -> "?addTag=$addTag"
            else -> ""
        }
        navController.popBackStack()
        tabsViewModel.tabs.open(noteId, params, eraseEpoch?.invoke())
    }
}

/**
 * The note editor. Records the id of its note on [entry] (also the id a new note gets on its
 * first save), so a background re-lock can return to it (see [RelockReturn]), and the vault's
 * erase epoch ([LocalEraseEpoch]) when it first showed it: the note is reopened only in that
 * vault.
 */
@Composable
private fun EditNoteDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val noteState by viewModel.state.collectAsState()
    val eraseEpoch = LocalEraseEpoch.current
    LaunchedEffect(entry, noteState.note.id, eraseEpoch) {
        val state = entry.savedStateHandle
        state[RelockReturn.OPEN_NOTE_ID] = noteState.note.id
        // Kept from the first time: an erase while the editor stays open does not adopt it.
        if (eraseEpoch != null && !state.contains(RelockReturn.OPEN_NOTE_EPOCH)) {
            state[RelockReturn.OPEN_NOTE_EPOCH] = eraseEpoch()
        }
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
    recoveryViewModel: LossRecoveryViewModel = metroViewModel(),
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
