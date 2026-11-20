package com.pasich.encly.presentation.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.ui.screens.ComposeScreenTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screens swap through [animationScreens] with the fade drawn by [screenFade] and not by
 * `fadeIn`/`fadeOut`: the leaving screen is still held while the swap runs and gone when it ends, a
 * swap with the lock screen is immediate, and the arriving screen is there at the end.
 */
class ScreenTransitionTest : ComposeScreenTest() {
    private companion object {
        const val SETTLED_MS = 1_000L
        const val IMMEDIATE_MS = 80L
    }

    private lateinit var controller: NavHostController

    private fun setHost() {
        setScreen {
            val navController = rememberNavController()
            controller = navController
            NavHost(navController, startDestination = NavRoutes.SettingsRoute.name) {
                listOf(NavRoutes.SettingsRoute, NavRoutes.AboutRoute, NavRoutes.LockRoute).forEach { route ->
                    animationScreens(route.name) { Box(Modifier.testTag(route.name).size(40.dp)) }
                }
            }
        }
        rule.onNodeWithTag(NavRoutes.SettingsRoute.name).assertExists()
    }

    private fun go(route: NavRoutes) {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { controller.navigate(route.name) }
    }

    @Test
    fun aSharedAxisSwapHoldsTheLeavingScreenThenDropsIt() {
        setHost()
        go(NavRoutes.AboutRoute)

        rule.mainClock.advanceTimeBy(IMMEDIATE_MS)
        rule.onNodeWithTag(NavRoutes.SettingsRoute.name).assertExists()
        rule.onNodeWithTag(NavRoutes.AboutRoute.name).assertExists()

        rule.mainClock.advanceTimeBy(SETTLED_MS)
        rule.onNodeWithTag(NavRoutes.SettingsRoute.name).assertDoesNotExist()
        rule.onNodeWithTag(NavRoutes.AboutRoute.name).assertExists()
    }

    @Test
    fun aSwapToTheLockScreenNeverKeepsTheScreenItLeaves() {
        setHost()
        val before = rule.mainClock.currentTime
        go(NavRoutes.LockRoute)

        rule.mainClock.advanceTimeBy(IMMEDIATE_MS)
        rule.onNodeWithTag(NavRoutes.SettingsRoute.name).assertDoesNotExist()
        rule.onNodeWithTag(NavRoutes.LockRoute.name).assertExists()
        assertTrue(rule.mainClock.currentTime - before <= IMMEDIATE_MS + 16)
    }

    @Test
    fun backPlaysTheSameMotionAndEndsOnTheScreenBelow() {
        setHost()
        go(NavRoutes.AboutRoute)
        rule.mainClock.advanceTimeBy(SETTLED_MS)

        rule.runOnUiThread { controller.popBackStack() }
        rule.mainClock.advanceTimeBy(IMMEDIATE_MS)
        rule.onNodeWithTag(NavRoutes.AboutRoute.name).assertExists()

        rule.mainClock.advanceTimeBy(SETTLED_MS)
        rule.onNodeWithTag(NavRoutes.AboutRoute.name).assertDoesNotExist()
        rule.onNodeWithTag(NavRoutes.SettingsRoute.name).assertExists()
    }
}
