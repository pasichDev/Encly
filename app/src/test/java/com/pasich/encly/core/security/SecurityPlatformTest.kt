package com.pasich.encly.core.security

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The thin Android adapters under the lock: clock, screen-off watcher, keyboard privacy flag. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecurityPlatformTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theSystemClockReportsTheBootCountSetting() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 7)

        assertEquals(7, SystemLockoutClock(context).bootCount())
    }

    @Test
    fun anUnknownBootCountIsMinusOne() {
        assertEquals(-1, SystemLockoutClock(context).bootCount())
    }

    @Test
    fun aBrokenSettingsProviderReadsAsAnUnknownBootCount() {
        val broken = object : android.content.ContextWrapper(context) {
            override fun getContentResolver() = throw SecurityException("no settings")
        }

        assertEquals(-1, SystemLockoutClock(broken).bootCount())
    }

    @Test
    fun theSystemClockNeverGoesBackwards() {
        val clock = SystemLockoutClock(context)
        val first = clock.elapsedRealtime()

        assertTrue(clock.elapsedRealtime() >= first)
    }

    @Test
    fun theJvmClockHasNoBootCountAndAdvances() {
        assertEquals(-1, JvmMonotonicClock.bootCount())
        val first = JvmMonotonicClock.elapsedRealtime()
        Thread.sleep(5)
        assertTrue(JvmMonotonicClock.elapsedRealtime() > first)
    }

    @Test
    fun theNoneWatcherReportsAnUnlockedDeviceAndIgnoresRegistrations() {
        var calls = 0
        val stop = DeviceLockWatcher.None.watchScreenOff { calls++ }
        stop()

        assertFalse(DeviceLockWatcher.None.isDeviceLocked())
        assertEquals(0, calls)
    }

    @Test
    fun theKeyguardStateDecidesWhetherTheDeviceIsLocked() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val watcher = SystemDeviceLockWatcher(context)

        shadowOf(keyguard).setIsDeviceLocked(true)
        assertTrue(watcher.isDeviceLocked())
        shadowOf(keyguard).setIsDeviceLocked(false)
        assertFalse(watcher.isDeviceLocked())
    }

    @Test
    fun screenOffFiresTheCallbackUntilTheWatchIsStopped() {
        var offs = 0
        val stop = SystemDeviceLockWatcher(context).watchScreenOff { offs++ }

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, offs)

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("another action is not a screen-off", 1, offs)

        stop()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, offs)
        stop() // stopping twice is harmless
    }

    @Test
    fun strictKeyboardPrivacyIsOffByDefaultAndPersistsWhenSet() {
        val prefs = InMemorySharedPreferences()
        val privacy = KeyboardPrivacy(prefs)
        assertFalse(privacy.strict.value)

        privacy.setStrict(true)

        assertTrue(privacy.strict.value)
        assertTrue(KeyboardPrivacy(prefs).strict.value)
        privacy.setStrict(false)
        assertFalse(KeyboardPrivacy(prefs).strict.value)
    }
}
