package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** The enter transition for a swap of the destinations whose routes are [from] and [to]. */
fun enterFor(from: String?, to: String?, forward: Boolean): EnterTransition = when (screenMotion(from, to)) {
    ScreenMotion.NONE -> EnterTransition.None
    ScreenMotion.FADE -> fadeScreenEnter()
    ScreenMotion.AXIS -> axisEnter(forward)
}

/** The exit transition for a swap of the destinations whose routes are [from] and [to]. */
fun exitFor(from: String?, to: String?, forward: Boolean): ExitTransition = when (screenMotion(from, to)) {
    ScreenMotion.NONE -> ExitTransition.None
    ScreenMotion.FADE -> fadeScreenExit()
    ScreenMotion.AXIS -> axisExit(forward)
}

/**
 * A destination with the app's screen motion (see [screenMotion]). Its [content] also gets the
 * destination's [AnimatedVisibilityScope] through [LocalNavAnimatedVisibilityScope], for shared
 * elements.
 */
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
    // decrypted screen on display (MainActivity's shield covers it until then). Unlock swaps
    // straight away too: it happens under the reveal overlay (see UnlockRevealOverlay).
    enterTransition = { enterFor(initialState.destination.route, targetState.destination.route, forward = true) },
    exitTransition = { exitFor(initialState.destination.route, targetState.destination.route, forward = true) },
    popEnterTransition = { enterFor(initialState.destination.route, targetState.destination.route, forward = false) },
    popExitTransition = { exitFor(initialState.destination.route, targetState.destination.route, forward = false) },
    content = { entry ->
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) { content(entry) }
    },
)
