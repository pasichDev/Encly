package com.pasich.encly.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
