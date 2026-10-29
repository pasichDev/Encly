package com.pasich.encly.presentation.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.presentation.designsystem.EnclyWordmark
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.drawerNavItems
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/** The destinations of the drawer and of the rail, with the counts the statistics report now. */
@Composable
internal fun rememberNavItems(statisticViewModel: StatisticViewModel = metroViewModel()): List<DrawerNavItem> {
    val totalTags by statisticViewModel.totalTagsCreated.collectAsStateWithLifecycle()
    val openTasks by statisticViewModel.openTasksCount.collectAsStateWithLifecycle()
    return drawerNavItems(
        tagCount = totalTags,
        openTasks = openTasks,
        donations = LocalAppInfo.current.donationsEnabled,
    )
}

/**
 * The navigation drawer of the expanded layout: [MainDrawer]'s wordmark and destinations, always there
 * beside the page instead of sliding over it. [selectedRoute] is the destination whose page is shown.
 */
@Composable
fun MainPermanentDrawer(selectedRoute: String, onItemClick: (DrawerNavItem) -> Unit, modifier: Modifier = Modifier) {
    val items = rememberNavItems()
    val appInfo = LocalAppInfo.current
    Column(
        modifier = modifier
            .width(EnclyTheme.spacing.drawerWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        EnclyWordmark(
            appName = appInfo.appName,
            modifier = Modifier.padding(
                start = EnclyTheme.spacing.gutter,
                top = EnclyTheme.spacing.m,
                bottom = EnclyTheme.spacing.m,
            ),
        )
        items.forEach { item ->
            DrawerEntry(item = item, isHome = item.route == selectedRoute, onClick = { onItemClick(item) })
        }
    }
}

/**
 * The navigation rail of the medium layout: the drawer's destinations as icons (a badge for a count, the
 * name as the description), in place of the menu button that opens the drawer on a phone.
 */
@Composable
fun MainRail(selectedRoute: String, onItemClick: (DrawerNavItem) -> Unit, modifier: Modifier = Modifier) {
    val items = rememberNavItems()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(EnclyTheme.spacing.railWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = EnclyTheme.spacing.xs),
    ) {
        items.forEach { item ->
            val label = stringResource(item.titleRes)
            NavigationRailItem(
                selected = item.route == selectedRoute,
                onClick = { onItemClick(item) },
                icon = { RailIcon(item, label) },
                label = { Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = false,
            )
        }
    }
}

@Composable
private fun RailIcon(item: DrawerNavItem, label: String) {
    val count = item.badgeCount?.takeIf { it > 0 }
    if (count == null) {
        Icon(imageVector = item.icon, contentDescription = label)
    } else {
        BadgedBox(badge = { Badge { Text(text = count.toString()) } }) {
            Icon(imageVector = item.icon, contentDescription = label)
        }
    }
}
