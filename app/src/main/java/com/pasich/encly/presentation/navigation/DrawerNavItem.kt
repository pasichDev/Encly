package com.pasich.encly.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyIcons

/** A drawer destination. [groupEnd] draws a hairline after it. */
data class DrawerNavItem(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val route: String,
    val badgeCount: Int? = null,
    val groupEnd: Boolean = false,
)

/**
 * The drawer's destinations, Notes first. Tasks is always listed (the home card hides itself
 * with nothing open, and the drawer is then the only way in); its badge counts open tasks.
 * [donations] lists Support, which only the builds that allow external donations carry.
 */
fun drawerNavItems(tagCount: Int, openTasks: Int, donations: Boolean): List<DrawerNavItem> = buildList {
    add(DrawerNavItem(R.string.notes_title, EnclyIcons.Paper, NavRoutes.HomeRoute.name))
    add(DrawerNavItem(R.string.main_drawer_tasks, EnclyIcons.Checklist, NavRoutes.TasksRoute.name, openTasks))
    add(DrawerNavItem(R.string.main_drawer_tags, EnclyIcons.Tag, NavRoutes.EditTagRoute.name, tagCount))
    add(DrawerNavItem(R.string.main_drawer_trash, EnclyIcons.Trash, NavRoutes.TrashRoute.name, groupEnd = true))
    add(
        DrawerNavItem(
            R.string.main_drawer_settings,
            EnclyIcons.Sliders,
            NavRoutes.SettingsRoute.name,
            groupEnd = true,
        ),
    )
    if (donations) add(DrawerNavItem(R.string.main_drawer_support, EnclyIcons.Coffee, NavRoutes.SupportRoute.name))
    add(DrawerNavItem(R.string.main_drawer_faq, EnclyIcons.Help, NavRoutes.FaqRoute.name))
    add(DrawerNavItem(R.string.about, EnclyIcons.Info, NavRoutes.AboutRoute.name))
}
