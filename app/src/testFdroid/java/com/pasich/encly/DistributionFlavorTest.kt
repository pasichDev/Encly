package com.pasich.encly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fdroid build must not surface Google Play and must upgrade the same install as play. */
class DistributionFlavorTest {

    @Test
    fun fdroidHidesStoreRating() {
        assertFalse(BuildConfig.STORE_RATING_ENABLED)
    }

    @Test
    fun fdroidShowsDonations() {
        assertTrue(BuildConfig.DONATIONS_ENABLED)
    }

    @Test
    fun fdroidKeepsTheSharedApplicationId() {
        assertEquals("com.pasich.encly", BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
    }
}
