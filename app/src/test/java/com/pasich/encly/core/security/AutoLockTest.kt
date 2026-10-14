package com.pasich.encly.core.security

import com.pasich.encly.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLockTest {
    @Test
    fun theDefaultIsFifteenSeconds() {
        val autoLock = AutoLock(InMemorySharedPreferences())

        assertEquals(AutoLockDelay.SECONDS_15, autoLock.delay.value)
        assertEquals(15_000L, autoLock.delayMillis)
    }

    @Test
    fun aChosenDelayIsKept() {
        val prefs = InMemorySharedPreferences()
        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_2)

        assertEquals(AutoLockDelay.MINUTES_2, AutoLock(prefs).delay.value)
    }

    @Test
    fun longerDelaysPersistWithoutChangingTheDefault() {
        val prefs = InMemorySharedPreferences()
        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_15)
        assertEquals(900_000L, AutoLock(prefs).delayMillis)

        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_30)
        assertEquals(1_800_000L, AutoLock(prefs).delayMillis)
        assertEquals(AutoLockDelay.SECONDS_15, AutoLock(InMemorySharedPreferences()).delay.value)
    }
}
