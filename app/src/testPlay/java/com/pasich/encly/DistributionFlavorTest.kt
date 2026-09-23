package com.pasich.encly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionFlavorTest {

    @Test
    fun playShowsStoreRating() {
        assertTrue(BuildConfig.STORE_RATING_ENABLED)
    }

    @Test
    fun playKeepsTheSharedApplicationId() {
        assertEquals("com.pasich.encly", BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
    }
}
