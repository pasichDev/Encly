package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.whats_new_dismiss
import com.pasich.encly.core.resources.whats_new_row_desc
import com.pasich.encly.core.resources.whats_new_title
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.AboutScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.viewmodel.StartupViewModel
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.whatsnew.Changelogs
import com.pasich.encly.whatsnew.WhatsNewTracker
import org.junit.Assert.assertEquals
import org.junit.Test

/** The "What's new" sheet: after an update on the home screen, and from Settings → About. */
class WhatsNewScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    /** The notes of this build, in the language of the test. */
    private val heading get() = checkNotNull(Changelogs.current("en")).heading

    /** The home screen of an install that last ran an older version. */
    private fun showHomeAfterAnUpdate(locked: Boolean = false) {
        val tracker = WhatsNewTracker(InMemoryKeyValueStore(), AppInfo.Default)
        val others: List<ViewModel> = app.home().filterNot { it is StartupViewModel }
        val owner = viewModels(app.startup(tracker), *others.toTypedArray())
        setNavScreen(owner, route = NavRoutes.HomeRoute.name) { nav ->
            CompositionLocalProvider(LocalVaultLocked provides locked) { MainRootScreen(nav) }
        }
    }

    @Test
    fun theSheetOpensOnTheHomeScreenAfterAnUpdateAndClosesOnTheButton() {
        showHomeAfterAnUpdate()

        waitForText(heading)
        rule.onNodeWithText(str(Res.string.whats_new_title)).assertIsDisplayed()

        rule.onNodeWithText(str(Res.string.whats_new_dismiss)).performClick()

        waitFor { countText(heading) == 0 }
    }

    @Test
    fun theSheetIsNeverDrawnWhileTheVaultIsLocked() {
        showHomeAfterAnUpdate(locked = true)

        rule.waitForIdle()
        assertEquals(0, countText(heading))
        assertEquals(0, countText(str(Res.string.whats_new_dismiss)))
    }

    @Test
    fun theSheetDoesNotOpenOnTheHomeScreenOfAnInstallThatHasSeenThisVersion() {
        setNavScreen(viewModels(*app.home()), route = NavRoutes.HomeRoute.name) { nav -> MainRootScreen(nav) }

        rule.waitForIdle()
        assertEquals(0, countText(str(Res.string.whats_new_dismiss)))
    }

    @Test
    fun aboutOffersTheNotesOfThisVersion() {
        setNavScreen(viewModels(), route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }
        waitForText(str(Res.string.whats_new_row_desc))

        rule.onNodeWithText(str(Res.string.whats_new_row_desc)).performScrollTo().performClick()

        waitForText(heading)
        rule.onNodeWithText(str(Res.string.whats_new_dismiss)).performClick()
        waitFor { countText(heading) == 0 }
    }
}
