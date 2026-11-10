package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private fun AnimatedContentTransitionScope<NavBackStackEntry>.motion(): ScreenMotion {
    val motion = screenMotion(initialState.destination.route, targetState.destination.route)
    ScreenMotions.record(initialState.id, motion)
    ScreenMotions.record(targetState.id, motion)
    return motion
}

/** The enter transition for a swap of the destinations: the move only, the fade is [screenFade]'s. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(forward: Boolean): EnterTransition = when (motion()) {
    ScreenMotion.NONE -> EnterTransition.None
    ScreenMotion.FADE -> holdEnter(ScreenMotion.FADE.enterFade()?.endMillis ?: 0)
    ScreenMotion.AXIS -> axisEnter(forward)
}

/** The exit transition for a swap of the destinations: the move only, the fade is [screenFade]'s. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(forward: Boolean): ExitTransition = when (motion()) {
    ScreenMotion.NONE -> ExitTransition.None
    ScreenMotion.FADE -> holdExit(ScreenMotion.FADE.exitFade()?.endMillis ?: 0)
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
    // decrypted screen on display (EnclyApp's shield covers it until then). Unlock swaps
    // straight away too: it happens under the reveal overlay (see UnlockRevealOverlay).
    enterTransition = { enterFor(forward = true) },
    exitTransition = { exitFor(forward = true) },
    popEnterTransition = { enterFor(forward = false) },
    popExitTransition = { exitFor(forward = false) },
    content = { entry ->
        val visibility = this
        val animationsOff = rememberAnimationsOff()
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides visibility) {
            Box(
                modifier = if (animationsOff) {
                    Modifier
                } else {
                    Modifier.screenFade(
                        visibility = visibility,
                        enter = { ScreenMotions.of(entry.id).enterFade() },
                        exit = { ScreenMotions.of(entry.id).exitFade() },
                    )
                },
                propagateMinConstraints = true,
            ) { visibility.content(entry) }
        }
    },
)
