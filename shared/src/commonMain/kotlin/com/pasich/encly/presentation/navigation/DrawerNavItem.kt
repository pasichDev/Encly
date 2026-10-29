package com.pasich.encly.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.about
import com.pasich.encly.core.resources.import_title
import com.pasich.encly.core.resources.main_drawer_archive
import com.pasich.encly.core.resources.main_drawer_faq
import com.pasich.encly.core.resources.main_drawer_settings
import com.pasich.encly.core.resources.main_drawer_support
import com.pasich.encly.core.resources.main_drawer_tags
import com.pasich.encly.core.resources.main_drawer_tasks
import com.pasich.encly.core.resources.main_drawer_trash
import com.pasich.encly.core.resources.notes_title
import com.pasich.encly.presentation.designsystem.EnclyIcons
import org.jetbrains.compose.resources.StringResource

/**
 * A drawer destination. [groupEnd] draws a hairline after it. A [highlighted] one is drawn as a
 * call-to-action button instead of a plain row (Support).
 */
data class DrawerNavItem(
    val titleRes: StringResource,
    val icon: ImageVector,
    val route: String,
    val badgeCount: Int? = null,
    val groupEnd: Boolean = false,
    val highlighted: Boolean = false,
)

/**
 * The drawer's destinations, in order: Notes (always first), Tasks, Tags, Archive and Trash, a
 * hairline, then Import notes directly above Settings, a hairline, Support (highlighted), FAQ
 * and About. Tasks is always listed (the home card hides itself with nothing open, and the drawer
 * is then the only way in); its badge counts open tasks. [donations] lists Support, which only
 * the builds that allow external donations carry; the Play build has none.
 */
fun drawerNavItems(tagCount: Int, openTasks: Int, donations: Boolean): List<DrawerNavItem> = buildList {
    add(DrawerNavItem(Res.string.notes_title, EnclyIcons.Paper, NavRoutes.HomeRoute.name))
    add(DrawerNavItem(Res.string.main_drawer_tasks, EnclyIcons.Checklist, NavRoutes.TasksRoute.name, openTasks))
    add(DrawerNavItem(Res.string.main_drawer_tags, EnclyIcons.Tag, NavRoutes.EditTagRoute.name, tagCount))
    add(DrawerNavItem(Res.string.main_drawer_archive, EnclyIcons.Archive, NavRoutes.ArchiveRoute.name))
    add(DrawerNavItem(Res.string.main_drawer_trash, EnclyIcons.Trash, NavRoutes.TrashRoute.name, groupEnd = true))
    add(DrawerNavItem(Res.string.import_title, EnclyIcons.Download, NavRoutes.ImportNotesRoute.name))
    add(
        DrawerNavItem(
            Res.string.main_drawer_settings,
            EnclyIcons.Sliders,
            NavRoutes.SettingsRoute.name,
            groupEnd = true,
        ),
    )
    if (donations) {
        add(
            DrawerNavItem(
                Res.string.main_drawer_support,
                EnclyIcons.Coffee,
                NavRoutes.SupportRoute.name,
                highlighted = true,
            ),
        )
    }
    add(DrawerNavItem(Res.string.main_drawer_faq, EnclyIcons.Help, NavRoutes.FaqRoute.name))
    add(DrawerNavItem(Res.string.about, EnclyIcons.Info, NavRoutes.AboutRoute.name))
}
