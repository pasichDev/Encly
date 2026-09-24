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
import androidx.compose.runtime.collectAsState
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
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyDrawerItem
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyWordmark
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
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
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    statisticViewModel: StatisticViewModel = hiltViewModel(),
) {
    val totalTags by statisticViewModel.totalTagsCreated.collectAsStateWithLifecycle()
    val totalTasks by statisticViewModel.totalTasksCreated.collectAsStateWithLifecycle()
    val showTasks by settingsViewModel.showTasksFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerItems = drawerNavItems(showTasks = showTasks, totalTags = totalTags, totalTasks = totalTasks)

    ModalDrawerSheet(
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
                EnclyDrawerItem(
                    label = item.title,
                    icon = item.icon,
                    badge = item.badgeCount,
                    onClick = {
                        scope.launch {
                            onItemClick(item)
                            drawerState.close()
                        }
                    },
                )
                if (item.groupEnd) EnclyGroupDivider(modifier = Modifier.padding(vertical = EnclyTheme.spacing.xs))
            }
        }
    }
}

/** The drawer's destinations; Tasks is listed only while it is not on the home screen. */
@Composable
private fun drawerNavItems(showTasks: Boolean, totalTags: Int, totalTasks: Int): List<DrawerNavItem> = buildList {
    add(DrawerNavItem(stringResource(R.string.main_drawer_tags), Lucide.Tag, NavRoutes.EditTagRoute.name, totalTags))
    if (!showTasks) {
        add(
            DrawerNavItem(
                stringResource(R.string.main_drawer_tasks),
                Lucide.ListChecks,
                NavRoutes.TasksRoute.name,
                totalTasks,
            ),
        )
    }
    add(
        DrawerNavItem(
            stringResource(R.string.main_drawer_trash),
            Lucide.Trash2,
            NavRoutes.TrashRoute.name,
            groupEnd = true,
        ),
    )
    add(
        DrawerNavItem(
            stringResource(R.string.main_drawer_settings),
            Lucide.SlidersHorizontal,
            NavRoutes.SettingsRoute.name,
            groupEnd = true,
        ),
    )
    add(DrawerNavItem(stringResource(R.string.main_drawer_support), Lucide.Coffee, NavRoutes.SupportRoute.name))
    add(DrawerNavItem(stringResource(R.string.main_drawer_faq), Lucide.CircleHelp, NavRoutes.FaqRoute.name))
    add(DrawerNavItem(stringResource(R.string.about), Lucide.Info, NavRoutes.AboutRoute.name))
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun getDrawerWidth(): Dp {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= TABLET_MIN_WIDTH_DP
    return if (isTablet) EnclyTheme.spacing.drawerWidth else configuration.screenWidthDp.dp * PHONE_DRAWER_FRACTION
}
