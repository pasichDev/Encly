package com.pasich.encly.presentation.navigation

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.import_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerNavItemsTest {

    private fun routes(donations: Boolean = true) =
        drawerNavItems(tagCount = 2, openTasks = 0, donations = donations).map { it.route }

    @Test
    fun tasksIsAlwaysListedEvenWithNothingOpen() {
        assertTrue(NavRoutes.TasksRoute.name in routes())
        assertTrue(NavRoutes.TasksRoute.name in routes(donations = false))
    }

    @Test
    fun notesComeFirstAsTheCurrentDestination() {
        assertEquals(NavRoutes.HomeRoute.name, routes().first())
    }

    @Test
    fun theDestinationsComeInTheAgreedOrder() {
        assertEquals(
            listOf(
                NavRoutes.HomeRoute,
                NavRoutes.TasksRoute,
                NavRoutes.EditTagRoute,
                NavRoutes.ArchiveRoute,
                NavRoutes.TrashRoute,
                NavRoutes.ImportNotesRoute,
                NavRoutes.SettingsRoute,
                NavRoutes.SupportRoute,
                NavRoutes.FaqRoute,
                NavRoutes.AboutRoute,
            ).map { it.name },
            routes(donations = true),
        )
        assertEquals(routes(donations = true) - NavRoutes.SupportRoute.name, routes(donations = false))
    }

    @Test
    fun importSitsDirectlyAboveSettings() {
        val routes = routes()
        assertEquals(
            routes.indexOf(NavRoutes.SettingsRoute.name) - 1,
            routes.indexOf(NavRoutes.ImportNotesRoute.name),
        )
    }

    @Test
    fun importIsLabelledLikeTheImportScreen() {
        val import = drawerNavItems(tagCount = 0, openTasks = 0, donations = false)
            .single { it.route == NavRoutes.ImportNotesRoute.name }
        assertEquals(Res.string.import_title, import.titleRes)
    }

    @Test
    fun trashEndsTheNotesGroupAndSettingsTheToolsGroup() {
        val items = drawerNavItems(tagCount = 0, openTasks = 0, donations = true)
        assertEquals(
            listOf(NavRoutes.TrashRoute.name, NavRoutes.SettingsRoute.name),
            items.filter { it.groupEnd }.map { it.route },
        )
    }

    @Test
    fun onlySupportIsHighlighted() {
        val items = drawerNavItems(tagCount = 0, openTasks = 0, donations = true)
        assertEquals(listOf(NavRoutes.SupportRoute.name), items.filter { it.highlighted }.map { it.route })
        assertTrue(drawerNavItems(tagCount = 0, openTasks = 0, donations = false).none { it.highlighted })
    }

    @Test
    fun theTasksBadgeCountsOpenTasks() {
        val tasks = drawerNavItems(tagCount = 0, openTasks = 3, donations = true)
            .single { it.route == NavRoutes.TasksRoute.name }
        assertEquals(3, tasks.badgeCount)
    }

    @Test
    fun supportIsListedOnlyWhereDonationsAreAllowed() {
        assertTrue(NavRoutes.SupportRoute.name in routes(donations = true))
        assertFalse(NavRoutes.SupportRoute.name in routes(donations = false))
    }
}
