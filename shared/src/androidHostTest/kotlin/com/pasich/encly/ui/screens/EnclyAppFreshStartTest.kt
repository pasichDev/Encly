package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.loss_hold_to_wipe
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.presentation.navigation.EnclyApp
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.LossRecoveryViewModel
import com.pasich.encly.presentation.viewmodel.ThemeViewModel
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.whatsnew.WhatsNewTracker
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import kotlin.reflect.KClass

/**
 * What the app root tells the version tracker. "Erase all data" and the loss-recovery wipe clear the recorded
 * version and go back to onboarding in the same process: the vault made there is new, so it must not get
 * "What's new" and the trash notice that an update from 2.x gets.
 */
class EnclyAppFreshStartTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    /** What a wipe leaves: a vault that was installed, with no version recorded. */
    private val clearedFlags = WhatsNewTracker(InMemoryKeyValueStore(), AppInfo.Default)

    private var controller: NavHostController? = null

    private fun route() = rule.runOnIdle { controller?.currentBackStackEntry?.destination?.route }

    private class Prebuilt(vararg viewModels: ViewModel) : MetroViewModelFactory() {
        override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> =
            viewModels.associate { it::class to { it } }
    }

    /** The app started on the damaged-vault screen: a vault that exists, so not a fresh install. */
    private fun showDamagedVault() {
        `when`(app.security.isDatabaseUnlocked()).thenReturn(false)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        val factory = Prebuilt(
            ThemeViewModel(app.settings),
            app.lock(),
            *app.home(),
            app.editor(),
            app.markdownExport(),
            app.onboarding(),
            app.authSetup(),
            app.startup(clearedFlags),
            LossRecoveryViewModel(app.security),
        )
        rule.setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
                EnclyApp(InitialStatus.LOSS_DATABASE, app.security, app.sessionLock, onNavController = {
                    controller = it
                })
            }
        }
        rule.waitForIdle()
    }

    private fun holdToWipe() {
        val button = rule.onNodeWithText(str(Res.string.loss_hold_to_wipe))
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_MS + 500)
        rule.waitForIdle()
    }

    @Test
    fun aVaultMadeAfterAWipeInTheSameProcessIsNotToldItWasUpdated() {
        showDamagedVault()
        holdToWipe()

        waitFor { route() == NavRoutes.OnboardingRoute.name }
        assertNull(clearedFlags.takeUpdate())
    }

    @Test
    fun anExistingVaultWithNoVersionRecordedIsStillToldItWasUpdatedFromBeforeTheTrashRetention() {
        showDamagedVault()

        assertEquals(NavRoutes.LossDataRoute.name, route())
        val update = clearedFlags.takeUpdate()
        assertNotNull(update)
        assertTrue(update!!.fromBeforeTrashRetention)
    }

    private companion object {
        const val HOLD_MS = 4_000L
    }
}
