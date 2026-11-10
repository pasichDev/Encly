package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class StorageUnreadableViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun retryingAnUnreadableStartAsksAgainAndNeverWipes() = runTest(dispatcher) {
        val security = mock(SecurityManager::class.java)
        `when`(security.resolveInitialStatus())
            .thenReturn(InitialStatus.STORAGE_UNREADABLE, InitialStatus.AUTH)
        val lock = SessionLockManager(security)
        val viewModel = StorageUnreadableViewModel(security, lock)
        val reported = mutableListOf<InitialStatus>()

        viewModel.retry { reported += it }
        awaitReports(reported, 1)

        assertEquals(listOf(InitialStatus.STORAGE_UNREADABLE), reported)
        assertTrue(viewModel.stillUnreadable.value)
        assertFalse(lock.locked.value)

        viewModel.retry { reported += it }
        awaitReports(reported, 2)

        assertEquals(InitialStatus.AUTH, reported.last())
        assertFalse(viewModel.stillUnreadable.value)
        assertTrue("a committed vault that is not open starts locked", lock.locked.value)
        verify(security, never()).wipeAndReset()
    }

    /** The retry resolves off the main thread: lets it finish, then runs what it posted to the test main. */
    private fun kotlinx.coroutines.test.TestScope.awaitReports(reported: List<InitialStatus>, count: Int) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (reported.size < count && System.nanoTime() < deadline) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
    }
}
