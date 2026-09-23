package com.pasich.encly.presentation.drawer

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
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
import com.pasich.encly.R
import com.pasich.encly.presentation.components.drawer.CustomNavigationDrawerItem
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import kotlinx.coroutines.launch

@Composable
fun MainDrawer(
    drawerState: DrawerState,
    onItemClick: (DrawerNavItem) -> Unit,
    modifier: Modifier = Modifier,
    onNoteClick: (Long) -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    statisticViewModel: StatisticViewModel = hiltViewModel(),
) {
    val totalTags by statisticViewModel.totalTagsCreated.collectAsStateWithLifecycle()
    val totalTasks by statisticViewModel.totalTasksCreated.collectAsStateWithLifecycle()
    val showTasks by settingsViewModel.showTasksFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerItems = drawerNavItems(showTasks = showTasks, totalTags = totalTags, totalTasks = totalTasks)

    ModalDrawerSheet(drawerShape = RectangleShape, modifier = modifier.width(getDrawerWidth())) {
        DrawerSearch(
            onNoteClick = { id ->
                onNoteClick(id)
                scope.launch { drawerState.close() }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                drawerItems.forEach { item ->
                    CustomNavigationDrawerItem(
                        item = item,
                        onItemClick = {
                            scope.launch {
                                onItemClick(item)
                                drawerState.close()
                            }
                        },
                    )

                    if (item.iconRes == R.drawable.ic_trash || item.iconRes == R.drawable.ic_settings) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp, horizontal = 25.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** The drawer's destinations; Tasks is listed only while it is not on the home screen. */
@Composable
private fun drawerNavItems(showTasks: Boolean, totalTags: Int, totalTasks: Int): List<DrawerNavItem> = buildList {
    add(
        DrawerNavItem(
            title = stringResource(R.string.main_drawer_tags),
            iconRes = R.drawable.ic_tags,
            route = NavRoutes.EditTagRoute.name,
            badgeCount = totalTags,
        ),
    )
    if (!showTasks) {
        add(
            DrawerNavItem(
                title = stringResource(R.string.main_drawer_tasks),
                iconRes = R.drawable.checklist,
                route = NavRoutes.TasksRoute.name,
                badgeCount = totalTasks,
            ),
        )
    }
    add(
        DrawerNavItem(
            title = stringResource(R.string.main_drawer_trash),
            iconRes = R.drawable.ic_trash,
            route = NavRoutes.TrashRoute.name,
        ),
    )
    add(
        DrawerNavItem(
            title = stringResource(R.string.main_drawer_settings),
            iconRes = R.drawable.ic_settings,
            route = NavRoutes.SettingsRoute.name,
        ),
    )
    add(
        DrawerNavItem(
            title = stringResource(R.string.main_drawer_support),
            iconRes = R.drawable.ic_cofee,
            route = NavRoutes.SupportRoute.name,
        ),
    )
    add(
        DrawerNavItem(
            title = stringResource(R.string.main_drawer_faq),
            iconRes = R.drawable.ic_faq,
            route = NavRoutes.FaqRoute.name,
        ),
    )
    add(
        DrawerNavItem(
            title = stringResource(R.string.about),
            iconRes = R.drawable.ic_about,
            route = NavRoutes.AboutRoute.name,
        ),
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun getDrawerWidth(): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = configuration.screenWidthDp >= 600

    return if (isTablet) {
        320.dp
    } else {
        screenWidth * 0.8f
    }
}
