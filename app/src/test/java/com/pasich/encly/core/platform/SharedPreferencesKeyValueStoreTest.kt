package com.pasich.encly.core.platform

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android [KeyValueStore] on real SharedPreferences: both write forms persist, and `commit` says so. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesKeyValueStoreTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("kv-test", Context.MODE_PRIVATE)
    private val store get() = SharedPreferencesKeyValueStore(prefs)

    @After
    fun clean() {
        prefs.edit().clear().commit()
    }

    @Test
    fun valuesOfEveryTypeAreReadBackAndDefaultsApplyToMissingKeys() {
        store.commit {
            putString("s", "text")
            putBoolean("b", true)
            putLong("l", 42L)
        }

        assertEquals("text", store.getString("s", null))
        assertTrue(store.getBoolean("b", false))
        assertEquals(42L, store.getLong("l", 0L))
        assertNull(store.getString("missing", null))
        assertEquals("fallback", store.getString("missing", "fallback"))
        assertFalse(store.getBoolean("missing", false))
        assertEquals(7L, store.getLong("missing", 7L))
        assertTrue(store.contains("s"))
        assertFalse(store.contains("missing"))
    }

    @Test
    fun editIsVisibleAtOnceAndReachesTheSharedPreferences() {
        store.edit { putBoolean("flag", true) }

        assertTrue(store.getBoolean("flag", false))
        assertTrue(prefs.getBoolean("flag", false))
    }

    @Test
    fun commitReportsThePersistedWrite() {
        assertTrue(store.commit { putString("k", "v") })
        assertEquals("v", prefs.getString("k", null))
    }

    @Test
    fun removeDropsOneKeyAndClearDropsAll() {
        store.commit {
            putString("a", "1")
            putString("b", "2")
        }

        store.commit { remove("a") }
        assertFalse(store.contains("a"))
        assertTrue(store.contains("b"))

        store.commit { clear() }
        assertFalse(store.contains("b"))
    }

    @Test
    fun aClearInTheSameBatchComesBeforeItsPuts() {
        store.commit { putString("old", "x") }

        store.commit {
            putString("new", "y")
            clear()
        }

        // SharedPreferences applies a clear before the puts of its batch; the store keeps that.
        assertFalse(store.contains("old"))
        assertEquals("y", store.getString("new", null))
    }
}
