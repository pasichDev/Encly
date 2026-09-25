package com.pasich.encly.ui.screens

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.testutil.anyCallback
import com.pasich.encly.testutil.eqValue
import com.pasich.encly.ui.theme.EnclyTheme
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The lock screen in a real FragmentActivity (what BiometricPrompt needs) that is recreated, as
 * on a rotation, while the biometric prompt is open. The LockViewModel outlives the activity, as
 * the navigation back stack keeps it; androidx.biometric drops the answer of a prompt whose
 * activity is destroyed, so the in-flight guard must not survive the activity, or the recreated
 * screen could never prompt again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LockScreenRecreationTest {
    /** Syncs with the Compose roots of the activities the scenario launches. */
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val app by lazy { TestApp(context) }
    private lateinit var lock: LockViewModel
    private var scenario: ActivityScenario<LockHostActivity>? = null

    @Before
    fun setUp() {
        `when`(app.security.isBiometricEnabled()).thenReturn(true)
        `when`(app.security.biometricAvailable()).thenReturn(true)
        // The prompt never answers on its own: it stays open until the activity goes.
        lock = app.lock()
        LockHostActivity.viewModels = FakeViewModels(lock)
        // A test-only activity: registered here rather than in the app's manifest.
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = LockHostActivity::class.java.name
                packageName = context.packageName
            },
        )
        scenario = ActivityScenario.launch(LockHostActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        LockHostActivity.viewModels?.viewModelStore?.clear()
        LockHostActivity.viewModels = null
    }

    @Test
    fun aRecreationDuringTheBiometricPromptPromptsAgain() {
        rule.waitUntil(WAIT_MS) { lock.biometricInFlight }
        val first = currentActivity()
        verify(app.security).requestBiometricKey(eqValue<FragmentActivity>(first), anyCallback())

        checkNotNull(scenario).recreate()
        val second = currentActivity()
        assertNotSame(first, second)

        // The recreated screen asks again, from the new activity, instead of waiting forever for
        // an answer the destroyed one can no longer deliver.
        rule.waitUntil(WAIT_MS) { promptsShown() == 2 }
        verify(app.security).requestBiometricKey(eqValue<FragmentActivity>(second), anyCallback())
        assertTrue(lock.biometricInFlight)
    }

    private fun currentActivity(): LockHostActivity {
        var activity: LockHostActivity? = null
        checkNotNull(scenario).onActivity { activity = it }
        return checkNotNull(activity)
    }

    /** How many biometric prompts the lock screen asked for. */
    private fun promptsShown(): Int = mockingDetails(app.security).invocations
        .count { it.method.name == "requestBiometricKey" }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}

/** Hosts [LockScreen] the way MainActivity does, with ViewModels that outlive the activity. */
class LockHostActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val owner = checkNotNull(viewModels)
        setContent {
            EnclyTheme(settings = ThemeSettings(), dark = false) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = NavRoutes.LockRoute.name) {
                    // Inside the destination, which is itself a ViewModelStoreOwner.
                    composable(NavRoutes.LockRoute.name) { ProvideViewModels(owner) { LockScreen(nav) } }
                    composable(NavRoutes.HomeRoute.name) { Text("stub:${NavRoutes.HomeRoute.name}") }
                }
            }
        }
    }

    companion object {
        var viewModels: FakeViewModels? = null
    }
}
