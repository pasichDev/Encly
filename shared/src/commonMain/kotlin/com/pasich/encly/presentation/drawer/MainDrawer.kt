package com.pasich.encly.presentation.drawer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.presentation.designsystem.EnclyDrawerHighlight
import com.pasich.encly.presentation.designsystem.EnclyDrawerItem
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyScrollColumn
import com.pasich.encly.presentation.designsystem.EnclyWordmark
import com.pasich.encly.presentation.effects.windowWidth
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.drawerNavItems
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val TABLET_MIN_WIDTH_DP = 600
private const val PHONE_DRAWER_FRACTION = 0.8f

/** The navigation drawer: the wordmark, then Encly's destinations with their counts. */
@Composable
fun MainDrawer(
    drawerState: DrawerState,
    onItemClick: (DrawerNavItem) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    statisticViewModel: StatisticViewModel = metroViewModel(),
) {
    val totalTags by statisticViewModel.totalTagsCreated.collectAsStateWithLifecycle()
    val openTasks by statisticViewModel.openTasksCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val appInfo = LocalAppInfo.current
    val drawerItems = drawerNavItems(
        tagCount = totalTags,
        openTasks = openTasks,
        donations = appInfo.donationsEnabled,
    )

    ModalDrawerSheet(
        drawerState = drawerState,
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerTonalElevation = 0.dp,
        // The sheet runs under the status bar (see MainRootScreen); its content is padded below it
        // explicitly, since App already consumed the status-bar inset.
        windowInsets = WindowInsets(0),
        modifier = modifier.width(getDrawerWidth()),
    ) {
        EnclyScrollColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = topInset),
        ) {
            EnclyWordmark(
                appName = appInfo.appName,
                modifier = Modifier.padding(
                    start = EnclyTheme.spacing.gutter,
                    top = EnclyTheme.spacing.m,
                    bottom = EnclyTheme.spacing.m,
                ),
            )
            drawerItems.forEach { item ->
                val isHome = item.route == NavRoutes.HomeRoute.name
                DrawerEntry(
                    item = item,
                    isHome = isHome,
                    onClick = {
                        scope.launch {
                            if (isHome) {
                                drawerState.close()
                            } else {
                                // The notes screen leaves with the drawer on it (a page
                                // transition), and the drawer shuts once it is out of sight.
                                onItemClick(item)
                                delay(DRAWER_SNAP_DELAY_MS)
                                drawerState.snapTo(DrawerValue.Closed)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** One destination: a row ([isHome]: the current one), or the highlighted Support button; a hairline may follow. */
@Composable
internal fun DrawerEntry(item: DrawerNavItem, isHome: Boolean, onClick: () -> Unit) {
    if (item.highlighted) {
        EnclyDrawerHighlight(label = stringResource(item.titleRes), icon = item.icon, onClick = onClick)
    } else {
        EnclyDrawerItem(
            label = stringResource(item.titleRes),
            icon = item.icon,
            // Zero reads as noise next to a destination; the badge shows only a real count.
            badge = item.badgeCount?.takeIf { it > 0 },
            // The modal drawer opens over the notes, so Notes is its current destination.
            selected = isHome,
            onClick = onClick,
        )
    }
    if (item.groupEnd) EnclyGroupDivider(modifier = Modifier.padding(vertical = EnclyTheme.spacing.xs))
}

/** Longer than the page transition, so the drawer never visibly snaps shut. */
private const val DRAWER_SNAP_DELAY_MS = 400L

@Composable
fun getDrawerWidth(): Dp {
    val widthDp = windowWidth()
    val isTablet = widthDp >= TABLET_MIN_WIDTH_DP.dp
    return if (isTablet) EnclyTheme.spacing.drawerWidth else widthDp * PHONE_DRAWER_FRACTION
}
