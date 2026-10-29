package com.pasich.encly.core.security

import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

/** The host is how a screen asking again after a recreation is told apart from the one that asked before. */
class ActivityBiometricHostTest {
    private val first = mock(FragmentActivity::class.java)
    private val second = mock(FragmentActivity::class.java)

    @Test
    fun hostsOfTheSameActivityAreEqual() {
        assertEquals(ActivityBiometricHost(first), ActivityBiometricHost(first))
        assertNotEquals(ActivityBiometricHost(first), ActivityBiometricHost(second))
    }

    @Test
    fun theActivityComesBackOutAndOnlyForThisAppsHosts() {
        assertSame(first, ActivityBiometricHost(first).activityOrNull())
        assertNull(object : BiometricHost {}.activityOrNull())
    }
}
