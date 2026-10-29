package com.pasich.encly.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.navigation.AppCommandRouter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * What the host asks the running UI to do: a desktop menu bar item or a dropped file, which have no
 * navigation controller of their own. A request is a route of `NavRoutes`, with its arguments if any, or an
 * [AppCommand] that the screens run the way a keyboard shortcut runs it (a new note opens as a tab where
 * there are tabs).
 */
class NavigationRequests {
    private val channel = Channel<String>(capacity = Channel.BUFFERED)
    private val commandChannel = Channel<AppCommand>(capacity = Channel.BUFFERED)

    /** The requested routes, each delivered once. */
    val routes: Flow<String> = channel.receiveAsFlow()

    /** The requested commands, each delivered once. */
    val commands: Flow<AppCommand> = commandChannel.receiveAsFlow()

    /** Asks for [route]; false if the queue is full (the request is dropped, never blocks). */
    fun request(route: String): Boolean = channel.trySend(route).isSuccess

    /** Asks for [command]; false if the queue is full (the request is dropped, never blocks). */
    fun request(command: AppCommand): Boolean = commandChannel.trySend(command).isSuccess
}

/** The host's navigation requests, or null where the host sends none (Android). */
val LocalNavigationRequests = staticCompositionLocalOf<NavigationRequests?> { null }

/**
 * Opens what [LocalNavigationRequests] asks for, but only while the vault is open ([isOpen]): a request that
 * arrives on the lock screen is dropped, so a menu item can never skip the lock. Commands go to [router].
 */
@Composable
internal fun HostNavigationRequests(
    navController: NavHostController,
    router: AppCommandRouter,
    isOpen: () -> Boolean,
) {
    val requests = LocalNavigationRequests.current ?: return
    val currentIsOpen by rememberUpdatedState(isOpen)
    LaunchedEffect(requests, navController) {
        launch {
            requests.routes.collect { route ->
                if (currentIsOpen()) navController.navigate(route) { launchSingleTop = true }
            }
        }
        launch {
            requests.commands.collect { command -> if (currentIsOpen()) router.run(command) }
        }
    }
}
