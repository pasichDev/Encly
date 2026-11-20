package com.pasich.encly.core.security

import com.pasich.encly.testutil.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoLockTest {
    @Test
    fun theDefaultIsFifteenSeconds() {
        val autoLock = AutoLock(InMemoryKeyValueStore())

        assertEquals(AutoLockDelay.SECONDS_15, autoLock.delay.value)
        assertEquals(15_000L, autoLock.delayMillis)
    }

    @Test
    fun aChosenDelayIsKept() {
        val prefs = InMemoryKeyValueStore()
        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_2)

        assertEquals(AutoLockDelay.MINUTES_2, AutoLock(prefs).delay.value)
    }

    @Test
    fun fiveAndTenMinutesPersistAndAStoredNameOfAnEarlierVersionStillReads() {
        val prefs = InMemoryKeyValueStore()
        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_5)
        assertEquals(300_000L, AutoLock(prefs).delayMillis)

        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_10)
        assertEquals(600_000L, AutoLock(prefs).delayMillis)

        // Delays are stored by name, so the ones added between others change no stored value.
        prefs.edit { putString("auto_lock_delay", "MINUTES_2") }
        assertEquals(AutoLockDelay.MINUTES_2, AutoLock(prefs).delay.value)
        prefs.edit { putString("auto_lock_delay", "MINUTES_15") }
        assertEquals(AutoLockDelay.MINUTES_15, AutoLock(prefs).delay.value)
    }

    @Test
    fun delaysAreOfferedFromShortestToLongest() {
        assertEquals(AutoLockDelay.entries.sortedBy { it.millis }, AutoLockDelay.entries)
    }

    @Test
    fun longerDelaysPersistWithoutChangingTheDefault() {
        val prefs = InMemoryKeyValueStore()
        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_15)
        assertEquals(900_000L, AutoLock(prefs).delayMillis)

        AutoLock(prefs).setDelay(AutoLockDelay.MINUTES_30)
        assertEquals(1_800_000L, AutoLock(prefs).delayMillis)
        assertEquals(AutoLockDelay.SECONDS_15, AutoLock(InMemoryKeyValueStore()).delay.value)
    }
}
