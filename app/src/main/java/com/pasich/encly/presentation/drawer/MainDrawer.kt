package com.pasich.encly.presentation.drawer

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.BuildConfig
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyDrawerItem
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyWordmark
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.drawerNavItems
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

private const val TABLET_MIN_WIDTH_DP = 600
private const val PHONE_DRAWER_FRACTION = 0.8f

/** The navigation drawer: the wordmark, then Encly's destinations with their counts. */
@Composable
fun MainDrawer(
    drawerState: DrawerState,
    onItemClick: (DrawerNavItem) -> Unit,
    modifier: Modifier = Modifier,
    statisticViewModel: StatisticViewModel = hiltViewModel(),
) {
    val totalTags by statisticViewModel.totalTagsCreated.collectAsStateWithLifecycle()
    val openTasks by statisticViewModel.openTasksCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerItems = drawerNavItems(
        tagCount = totalTags,
        openTasks = openTasks,
        donations = BuildConfig.DONATIONS_ENABLED,
    )

    ModalDrawerSheet(
        drawerState = drawerState,
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerTonalElevation = 0.dp,
        modifier = modifier.width(getDrawerWidth()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            EnclyWordmark(
                appName = stringResource(R.string.app_name),
                modifier = Modifier.padding(
                    start = EnclyTheme.spacing.gutter,
                    top = EnclyTheme.spacing.m,
                    bottom = EnclyTheme.spacing.m,
                ),
            )
            drawerItems.forEach { item ->
                val isHome = item.route == NavRoutes.HomeRoute.name
                EnclyDrawerItem(
                    label = stringResource(item.titleRes),
                    icon = item.icon,
                    // Zero reads as noise next to a destination; the badge shows only a real count.
                    badge = item.badgeCount?.takeIf { it > 0 },
                    // The drawer opens over the notes, so Notes is the current destination.
                    selected = isHome,
                    onClick = {
                        scope.launch {
                            if (!isHome) onItemClick(item)
                            drawerState.close()
                        }
                    },
                )
                if (item.groupEnd) EnclyGroupDivider(modifier = Modifier.padding(vertical = EnclyTheme.spacing.xs))
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun getDrawerWidth(): Dp {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= TABLET_MIN_WIDTH_DP
    return if (isTablet) EnclyTheme.spacing.drawerWidth else configuration.screenWidthDp.dp * PHONE_DRAWER_FRACTION
}
