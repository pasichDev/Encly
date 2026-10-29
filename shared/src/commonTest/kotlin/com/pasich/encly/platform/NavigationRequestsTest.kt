package com.pasich.encly.platform

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationRequestsTest {
    @Test
    fun requestsAreDeliveredOnceInOrder() = runTest {
        val requests = NavigationRequests()
        assertTrue(requests.request("BackupRoute"))
        assertTrue(requests.request("SettingsRoute"))
        assertEquals("BackupRoute", requests.routes.first())
        assertEquals("SettingsRoute", requests.routes.first())
    }

    @Test
    fun aFullQueueDropsTheRequestInsteadOfBlocking() {
        val requests = NavigationRequests()
        var accepted = 0
        repeat(1_000) { if (requests.request("HomeRoute")) accepted++ }
        assertTrue(accepted in 1..999, "the queue is bounded: $accepted")
    }
}
