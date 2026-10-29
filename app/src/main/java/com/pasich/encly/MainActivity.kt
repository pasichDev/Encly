package com.pasich.encly

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.pasich.encly.core.di.appGraph
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.core.security.BiometricTextSource
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.platform.AndroidHostServices
import com.pasich.encly.presentation.navigation.EnclyApp
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * AppCompatActivity (a FragmentActivity, as BiometricPrompt needs) so the in-app language set
 * through AppCompatDelegate.setApplicationLocales is applied on every API level, not only on
 * Android 13+ where the framework does it.
 */
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var sessionLockManager: SessionLockManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var keyboardPrivacy: KeyboardPrivacy

    @Inject
    lateinit var biometricTexts: BiometricTextSource

    @Inject
    lateinit var hostServices: AndroidHostServices

    override fun onCreate(savedInstanceState: Bundle?) {
        appGraph.inject(this)
        // Encly always renders protected content. Apply FLAG_SECURE before the splash/content
        // lifecycle starts so screenshots, screen recording, casting and recents snapshots
        // cannot capture an unprotected first frame.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Before NavHost reads the intent: no caller picks a screen through nav deep-link extras.
        stripNavigationExtras(intent)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The strings follow the process's default locale: put the in-app language there before composing.
        AppLocales.syncDefaultLocale()
        // After super.onCreate: touching decorView earlier builds the window with the splash
        // theme, which has an action bar, instead of Theme.Encly.
        protectWindow()
        enableEdgeToEdge()
        supportActionBar?.hide()

        // Resolve the startup state and read the theme off the main thread (Keystore/crypto/
        // prefs reads); keep the splash visible until both are ready, so the first frame is
        // drawn in the stored palette, mode and fonts rather than the defaults.
        var initialStatus by mutableStateOf<InitialStatus?>(null)
        splashScreen.setKeepOnScreenCondition { initialStatus == null }

        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                // Never throws: unreadable vault state routes to the damaged-vault screen.
                val status = securityManager.resolveInitialStatus()
                try {
                    settingsRepository.loadThemeSettings(existingInstall = !securityManager.isOnboardingShow())
                } catch (_: IOException) {
                    // An unreadable settings file: the theme starts from its defaults.
                }
                status
            }
            // A committed vault that is not open starts locked, before the first frame, so a back
            // stack restored after process death is replaced by the lock screen, never shown.
            if (status == InitialStatus.AUTH) sessionLockManager.requireUnlock()
            // The system biometric prompt is built outside any coroutine: give it finished texts first.
            biometricTexts.refresh()
            initialStatus = status
        }

        setContent {
            // Auto re-lock and the navigation guards live in the shared UI (EnclyApp).
            val strictKeyboard by keyboardPrivacy.strict.collectAsState()
            hostServices.Provide(this, strictKeyboard) {
                EnclyApp(
                    startStatus = initialStatus,
                    securityManager = securityManager,
                    sessionLockManager = sessionLockManager,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        stripNavigationExtras(intent)
        super.onNewIntent(intent)
    }
}

/**
 * Window-level privacy: no autofill service sees any field (notes are not form data), the
 * content is marked sensitive for accessibility services that are not accessibility tools
 * (TalkBack still reads it), and other apps' overlays are hidden while Encly is in front.
 * Every activity calls it after super.onCreate, next to FLAG_SECURE set before it.
 */
internal fun Activity.protectWindow() {
    window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        window.decorView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window.setHideOverlayWindows(true)
    }
}

/** Navigation's own deep-link extras; the exported launcher activity must not honour them. */
private const val NAV_EXTRA_PREFIX = "android-support-nav:controller:"

private fun stripNavigationExtras(intent: Intent?) {
    val extras = intent?.extras ?: return
    extras.keySet().filter { it.startsWith(NAV_EXTRA_PREFIX) }.forEach(intent::removeExtra)
}
