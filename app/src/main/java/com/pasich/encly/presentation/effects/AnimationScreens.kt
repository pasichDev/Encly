package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pasich.encly.presentation.navigation.NavRoutes

private val LOCK_ROUTE = NavRoutes.LockRoute.name

fun NavGraphBuilder.animationScreens(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    // Re-lock swaps straight to the lock screen: no cross-fade that would keep the previous,
    // decrypted screen on display (MainActivity's shield covers it until then).
    enterTransition = {
        if (targetState.destination.route == LOCK_ROUTE) {
            EnterTransition.None
        } else if (initialState.destination.route == LOCK_ROUTE) {
            unlockScreenEnterAnimation()
        } else {
            defaultScreenEnterAnimation()
        }
    },
    exitTransition = {
        if (targetState.destination.route == LOCK_ROUTE) {
            ExitTransition.None
        } else {
            defaultScreenExitAnimation()
        }
    },
    popEnterTransition = { defaultScreenEnterAnimation() },
    popExitTransition = { defaultScreenExitAnimation() },
    content = content,
)
