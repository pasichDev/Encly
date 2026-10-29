package com.pasich.encly.platform

import com.pasich.encly.presentation.designsystem.AppCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationCommandRequestsTest {
    @Test
    fun commandsAreDeliveredOnceInOrderApartFromRoutes() = runTest {
        val requests = NavigationRequests()
        assertTrue(requests.request(AppCommand.NewNote))
        assertTrue(requests.request("BackupRoute"))
        assertTrue(requests.request(AppCommand.LockApp))

        assertEquals(AppCommand.NewNote, requests.commands.first())
        assertEquals(AppCommand.LockApp, requests.commands.first())
        assertEquals("BackupRoute", requests.routes.first())
    }

    @Test
    fun aFullCommandQueueDropsTheRequestInsteadOfBlocking() {
        val requests = NavigationRequests()
        var accepted = 0
        repeat(1_000) { if (requests.request(AppCommand.Dismiss)) accepted++ }
        assertTrue(accepted in 1..999, "the queue is bounded: $accepted")
    }
}
