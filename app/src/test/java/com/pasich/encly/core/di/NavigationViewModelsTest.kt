package com.pasich.encly.core.di

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.screens.TestRoutes
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ViewModels asked for inside a navigation destination, the way every screen does: the graph's
 * factory builds them, and an assisted one gets the destination's arguments as its saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationViewModelsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aDestinationViewModelReadsItsNavigationArguments() {
        val graph = createGraphFactory<ContributionsGraph.Factory>().create(context)
        var editor: EditNoteViewModel? = null
        lateinit var navigate: (String) -> Unit

        rule.setContent {
            val nav = rememberNavController()
            navigate = nav::navigate
            CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                NavHost(navController = nav, startDestination = "start") {
                    composable("start") { Text("start") }
                    composable(TestRoutes.EDIT_NOTE, arguments = TestRoutes.argumentsFor(TestRoutes.EDIT_NOTE)) {
                        editor = metroViewModel<EditNoteViewModel>()
                        Text("editor")
                    }
                }
            }
        }
        rule.runOnIdle { navigate("${NavRoutes.EditNoteRoute.name}/-1?isReadTrashOnly=true") }
        rule.waitForIdle()

        val opened = checkNotNull(editor)
        assertTrue(opened.isReadTrashOnly)
        assertTrue(opened.isNewNote)
    }

    @Test
    fun twoDestinationsDoNotShareAViewModel() {
        val graph = createGraphFactory<ContributionsGraph.Factory>().create(context)
        val seen = mutableListOf<EditNoteViewModel>()
        lateinit var navigate: (String) -> Unit

        rule.setContent {
            val nav = rememberNavController()
            navigate = nav::navigate
            CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                NavHost(navController = nav, startDestination = "start") {
                    composable("start") { Text("start") }
                    composable(TestRoutes.EDIT_NOTE, arguments = TestRoutes.argumentsFor(TestRoutes.EDIT_NOTE)) {
                        val editor = metroViewModel<EditNoteViewModel>()
                        if (seen.none { it === editor }) seen += editor
                        Text("editor")
                    }
                }
            }
        }
        rule.runOnIdle { navigate("${NavRoutes.EditNoteRoute.name}/-1") }
        rule.waitForIdle()
        rule.runOnIdle { navigate("${NavRoutes.EditNoteRoute.name}/-1?isReadTrashOnly=true") }
        rule.waitForIdle()

        assertEquals(2, seen.size)
        assertNotSame(seen[0], seen[1])
        assertFalse(seen[0].isReadTrashOnly)
        assertTrue(seen[1].isReadTrashOnly)
    }
}
