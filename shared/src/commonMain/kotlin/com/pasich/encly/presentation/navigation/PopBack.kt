package com.pasich.encly.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController

/**
 * Back from a screen's own arrow: pops the screen on top, once. A second tap that lands while that
 * screen is still leaving finds the screen under it not yet resumed and does nothing; a plain
 * `popBackStack()` would pop that one too, and from the first screen leave an empty window.
 */
fun NavHostController.popBack(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED && popBackStack()

/**
 * Opens [route] from a screen's own row, once: a second tap while the screen is still leaving does nothing
 * (the screen on top is no longer resumed), and a tap on a screen that is already open is not pushed again.
 */
fun NavHostController.openRoute(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route) { launchSingleTop = true }
    }
}
