package com.pasich.encly.ios.security

import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The guard against a stand-in for iOS' background tasks. */
class BackgroundLockGuardTest {
    private class FakeTasks(var grant: Boolean = true) : BackgroundTasks {
        var expire: (() -> Unit)? = null
        val begun = mutableListOf<String>()
        val ended = mutableListOf<UIBackgroundTaskIdentifier>()

        override fun begin(name: String, onExpire: () -> Unit): UIBackgroundTaskIdentifier {
            begun += name
            expire = onExpire
            return if (grant) 7uL else UIBackgroundTaskInvalid
        }

        override fun end(task: UIBackgroundTaskIdentifier) {
            ended += task
        }
    }

    private val tasks = FakeTasks()
    private var open = true
    private var locks = 0

    /** A lock that closes the vault on the spot, like a lock with no editor to wait for. */
    private val synchronousLock: () -> Unit = {
        locks++
        open = false
    }

    /** A lock that has to wait for an editor save: the vault is still open when it returns. */
    private val waitingLock: () -> Unit = { locks++ }

    private fun guard(lock: () -> Unit) = BackgroundLockGuard({ open }, lock, tasks)

    @Test
    fun nothingStartsWithoutAnOpenVault() {
        open = false

        guard(synchronousLock).begin()

        assertTrue(tasks.begun.isEmpty())
    }

    @Test
    fun aRefusedTaskLocksAtOnce() {
        tasks.grant = false
        val guard = guard(waitingLock)

        guard.begin()

        assertEquals(1, locks)
        assertTrue(tasks.ended.isEmpty())
    }

    @Test
    fun expiryLocksAndGivesTheTaskBackOnceTheVaultIsClosed() {
        val guard = guard(synchronousLock)
        guard.begin()

        tasks.expire!!.invoke()

        assertEquals(1, locks)
        assertEquals(listOf<UIBackgroundTaskIdentifier>(7uL), tasks.ended)
    }

    @Test
    fun expiryWithTheVaultStillOpenKeepsTheTaskUntilTheLockReportsLocked() {
        val guard = guard(waitingLock)
        guard.begin()

        tasks.expire!!.invoke()

        assertEquals(1, locks)
        assertTrue(tasks.ended.isEmpty())

        // The lock closes the vault after its wait; the app's `locked` collector then ends the task.
        open = false
        guard.end()
        assertEquals(listOf<UIBackgroundTaskIdentifier>(7uL), tasks.ended)
        guard.end()
        assertEquals(1, tasks.ended.size)
    }

    @Test
    fun onlyOneTaskRunsAtATime() {
        val guard = guard(waitingLock)

        guard.begin()
        guard.begin()

        assertEquals(1, tasks.begun.size)
    }
}
