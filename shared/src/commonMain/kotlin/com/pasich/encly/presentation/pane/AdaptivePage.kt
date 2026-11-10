package com.pasich.encly.presentation.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import com.pasich.encly.presentation.drawer.MainPermanentDrawer
import com.pasich.encly.presentation.drawer.MainRail
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.LocalWidthClass
import com.pasich.encly.ui.theme.WidthClass

/**
 * [content] as a page of the app: in a compact window it fills the window as it always did; in a wider one
 * it is held to [com.pasich.encly.ui.theme.EnclySpacing.contentMaxWidth] and centred, so lines of text and
 * forms do not stretch across a desktop screen.
 */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val wide = LocalWidthClass.current.isWide
    Box(
        modifier = if (wide) modifier.fillMaxSize() else modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = if (wide) {
                Modifier.widthIn(max = EnclyTheme.spacing.contentMaxWidth).fillMaxHeight()
            } else {
                Modifier
            },
        ) { content() }
    }
}

/**
 * A page that belongs to one of the drawer's destinations ([selected]), with the navigation the window size
 * calls for: nothing around [content] on a phone (it is reached from the drawer and goes back to it), a
 * navigation rail beside it in a medium window, the permanent drawer in an expanded one.
 */
@Composable
fun NavigationPage(
    navController: NavHostController,
    selected: NavRoutes,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val widthClass = LocalWidthClass.current
    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (widthClass.isWide) PageNavigation(widthClass, selected, navController)
        CenteredContent(modifier = Modifier.weight(1f)) { content() }
    }
}

/** The rail or the permanent drawer for a [widthClass] that is not compact. */
@Composable
internal fun PageNavigation(widthClass: WidthClass, selected: NavRoutes, navController: NavHostController) {
    val onItemClick: (DrawerNavItem) -> Unit = { item -> navController.openDestination(item, from = selected) }
    if (widthClass == WidthClass.Expanded) {
        MainPermanentDrawer(selectedRoute = selected.name, onItemClick = onItemClick)
    } else {
        MainRail(selectedRoute = selected.name, onItemClick = onItemClick)
    }
}

/**
 * Goes to the destination [item] from the page [from], one of the drawer's. Notes is the bottom of the back
 * stack; any other destination replaces the page above it, so the stack never grows with every click on the
 * navigation.
 */
internal fun NavHostController.openDestination(item: DrawerNavItem, from: NavRoutes) {
    when {
        item.route == from.name -> Unit

        item.route == NavRoutes.HomeRoute.name -> popBackStack(NavRoutes.HomeRoute.name, inclusive = false)

        from == NavRoutes.HomeRoute -> navigate(
            item.route,
            NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true).build(),
        )

        else -> navigate(item.route) {
            popUpTo(NavRoutes.HomeRoute.name) { inclusive = false }
            launchSingleTop = true
        }
    }
}
