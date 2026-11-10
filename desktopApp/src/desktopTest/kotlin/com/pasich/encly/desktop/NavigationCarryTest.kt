package com.pasich.encly.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.SavedState
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * What the desktop does on a language change (see `DesktopApp`): the app is rebuilt under a new key, and the back stack
 * of the controller it replaces is handed to the new one before its graph is set. The screen stays where it was and
 * the entries keep their ViewModels (their stores belong to the window and are found by the entry's id), instead of
 * the new controller starting at the start destination and the old entries' ViewModels staying behind.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationCarryTest {
    private class ScreenViewModel : ViewModel()

    private class Rig {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        var revision by mutableIntStateOf(0)
        var controller: NavHostController? = null
        var screenViewModel: ScreenViewModel? = null
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setUpRig(rig: Rig, carry: Boolean) {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides rig.owner) {
                val revision = rig.revision
                val carried: SavedState? = remember(revision) { if (carry) rig.controller?.saveState() else null }
                key(revision) {
                    val navController = rememberNavController()
                    remember(navController) {
                        if (carried != null) navController.restoreState(carried)
                        Unit
                    }
                    SideEffect { rig.controller = navController }
                    NavHost(navController, startDestination = "start") {
                        composable("start") { Text("start screen") }
                        composable("note") {
                            rig.screenViewModel =
                                viewModel(factory = viewModelFactory { initializer { ScreenViewModel() } })
                            Text("note screen")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `the screen and its ViewModel survive a rebuild that carries the back stack`() = runComposeUiTest {
        val rig = Rig()
        setUpRig(rig, carry = true)
        runOnIdle { rig.controller!!.navigate("note") }
        waitForIdle()
        onNodeWithText("note screen").assertExists()
        val before = assertNotNull(rig.screenViewModel)

        rig.revision = 1
        waitForIdle()

        onNodeWithText("note screen").assertExists()
        assertSame(before, rig.screenViewModel)
    }

    @Test
    fun `without carrying the back stack the rebuilt app starts over`() = runComposeUiTest {
        val rig = Rig()
        setUpRig(rig, carry = false)
        runOnIdle { rig.controller!!.navigate("note") }
        waitForIdle()
        onNodeWithText("note screen").assertExists()

        rig.revision = 1
        waitForIdle()

        onNodeWithText("start screen").assertExists()
    }
}
