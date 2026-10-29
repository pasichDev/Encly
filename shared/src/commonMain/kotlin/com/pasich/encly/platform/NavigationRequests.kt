package com.pasich.encly.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Screens the host asks the running UI to open: a desktop menu bar item or a dropped file, which have no
 * navigation controller of their own. The request is a route of `NavRoutes`, with its arguments if any.
 */
class NavigationRequests {
    private val channel = Channel<String>(capacity = Channel.BUFFERED)

    /** The requests, each delivered once. */
    val routes: Flow<String> = channel.receiveAsFlow()

    /** Asks for [route]; false if the queue is full (the request is dropped, never blocks). */
    fun request(route: String): Boolean = channel.trySend(route).isSuccess
}

/** The host's navigation requests, or null where the host sends none (Android). */
val LocalNavigationRequests = staticCompositionLocalOf<NavigationRequests?> { null }

/**
 * Opens what [LocalNavigationRequests] asks for, but only while the vault is open: a request that arrives
 * on the lock screen is dropped, so a menu item can never skip the lock.
 */
@Composable
internal fun HostNavigationRequests(navController: NavHostController, isDatabaseUnlocked: () -> Boolean) {
    val requests = LocalNavigationRequests.current ?: return
    val currentIsUnlocked by rememberUpdatedState(isDatabaseUnlocked)
    LaunchedEffect(requests, navController) {
        requests.routes.collect { route ->
            if (currentIsUnlocked()) navController.navigate(route) { launchSingleTop = true }
        }
    }
}
