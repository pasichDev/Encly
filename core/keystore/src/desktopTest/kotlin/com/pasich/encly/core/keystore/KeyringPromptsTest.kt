package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.KeyStoreProblem
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The keyring's prompts, played against a Secret Service of our own: what is asked, answered, dismissed, timed out. */
class KeyringPromptsTest {
    /** A service whose state and answers the test sets; prompts answer from [answers] when started. */
    private class FakeBus : KeyringBus {
        var default: String? = "/c/default"
        var locked = false
        var unlockPrompt: String? = null
        var unlocksOnPrompt = true
        var created = CreatedCollection("/c/new", null)
        val answers = mutableMapOf<String, PromptOutcome?>()
        val calls = CopyOnWriteArrayList<String>()
        private val watchers = mutableMapOf<String, (PromptOutcome) -> Unit>()

        override fun defaultCollection(): String? = default

        override fun isLocked(collection: String): Boolean = locked

        override fun unlock(objects: List<String>): String? {
            calls += "unlock ${objects.joinToString()}"
            if (unlockPrompt == null) locked = false
            return unlockPrompt
        }

        override fun createDefaultCollection(): CreatedCollection {
            calls += "create"
            return created
        }

        override fun watchPrompt(prompt: String, completed: (PromptOutcome) -> Unit): AutoCloseable {
            calls += "watch $prompt"
            watchers[prompt] = completed
            return AutoCloseable { calls += "unwatch $prompt" }
        }

        override fun startPrompt(prompt: String) {
            calls += "start $prompt"
            val answer = answers[prompt] ?: return
            if (!answer.dismissed && unlocksOnPrompt) locked = false
            watchers[prompt]?.invoke(answer)
        }

        override fun dismissPrompt(prompt: String) {
            calls += "dismiss $prompt"
        }
    }

    private val bus = FakeBus()
    private val prompts = KeyringPrompts(timeoutMillis = 300, pollMillis = 10)

    private fun problemOf(block: () -> Unit): KeyStoreProblem? {
        val failure = assertFailsWith<SecretStoreException> { block() }
        assertFalse(failure.lost)
        assertTrue(failure.cause is SecretStoreUnavailableException)
        return failure.problem
    }

    @Test
    fun anOpenDefaultCollectionIsReturnedWithoutAnyPrompt() {
        assertEquals("/c/default", prompts.openDefaultCollection(bus, createMissing = true))
        assertTrue(bus.calls.isEmpty())
    }

    @Test
    fun aMissingDefaultCollectionIsNullWhenNotToBeMade() {
        bus.default = null

        assertNull(prompts.openDefaultCollection(bus, createMissing = false))
        assertTrue(bus.calls.isEmpty())
    }

    @Test
    fun aMissingDefaultCollectionIsMadeThroughTheService() {
        bus.default = null

        assertEquals("/c/new", prompts.openDefaultCollection(bus, createMissing = true))
        assertEquals(listOf("create"), bus.calls)
    }

    @Test
    fun aCollectionTheServiceMakesAfterAPromptIsTheOneThePromptNames() {
        bus.default = null
        bus.created = CreatedCollection(null, "/p/1")
        bus.answers["/p/1"] = PromptOutcome(dismissed = false, result = "/c/made")

        assertEquals("/c/made", prompts.openDefaultCollection(bus, createMissing = true))
        assertEquals(listOf("create", "watch /p/1", "start /p/1", "unwatch /p/1"), bus.calls)
    }

    @Test
    fun aCreationThatGivesNoCollectionIsAMissingDefault() {
        bus.default = null
        bus.created = CreatedCollection(null, "/p/1")
        bus.answers["/p/1"] = PromptOutcome(dismissed = false, result = null)

        assertEquals(KeyStoreProblem.NO_DEFAULT, problemOf { prompts.openDefaultCollection(bus, true) })

        bus.created = CreatedCollection(null, null)
        assertEquals(KeyStoreProblem.NO_DEFAULT, problemOf { prompts.openDefaultCollection(bus, true) })
    }

    @Test
    fun aCreationThePromptDismissedIsDismissed() {
        bus.default = null
        bus.created = CreatedCollection(null, "/p/1")
        bus.answers["/p/1"] = PromptOutcome(dismissed = true, result = null)

        assertEquals(KeyStoreProblem.DISMISSED, problemOf { prompts.openDefaultCollection(bus, true) })
    }

    @Test
    fun aLockedCollectionIsUnlockedWithoutAPromptWhenTheServiceNeedsNone() {
        bus.locked = true

        assertEquals("/c/default", prompts.openDefaultCollection(bus, createMissing = false))
        assertEquals(listOf("unlock /c/default"), bus.calls)
    }

    @Test
    fun aLockedCollectionIsUnlockedThroughThePromptTheServiceReturns() {
        bus.locked = true
        bus.unlockPrompt = "/p/2"
        bus.answers["/p/2"] = PromptOutcome(dismissed = false, result = null)

        assertEquals("/c/default", prompts.openDefaultCollection(bus, createMissing = false))
        assertEquals(listOf("unlock /c/default", "watch /p/2", "start /p/2", "unwatch /p/2"), bus.calls)
    }

    @Test
    fun aDismissedUnlockPromptIsDismissed() {
        bus.locked = true
        bus.unlockPrompt = "/p/2"
        bus.answers["/p/2"] = PromptOutcome(dismissed = true, result = null)

        assertEquals(KeyStoreProblem.DISMISSED, problemOf { prompts.openDefaultCollection(bus, false) })
    }

    @Test
    fun aCollectionThatStaysLockedAfterThePromptIsLocked() {
        bus.locked = true
        bus.unlockPrompt = "/p/2"
        bus.unlocksOnPrompt = false
        bus.answers["/p/2"] = PromptOutcome(dismissed = false, result = null)

        assertEquals(KeyStoreProblem.LOCKED, problemOf { prompts.openDefaultCollection(bus, false) })
    }

    @Test
    fun itemsFoundLockedAreUnlockedThroughTheirPrompt() {
        bus.unlockPrompt = "/p/3"
        bus.answers["/p/3"] = PromptOutcome(dismissed = false, result = null)

        prompts.unlockAll(bus, listOf("/i/1", "/i/2"))
        assertEquals(listOf("unlock /i/1, /i/2", "watch /p/3", "start /p/3", "unwatch /p/3"), bus.calls)

        bus.calls.clear()
        bus.unlockPrompt = null
        prompts.unlockAll(bus, listOf("/i/1"))
        assertEquals(listOf("unlock /i/1"), bus.calls)
    }

    @Test
    fun noPromptIsNoOutcome() {
        assertNull(prompts.complete(bus, null))
        assertTrue(bus.calls.isEmpty())
    }

    @Test
    fun aPromptNobodyAnswersEndsAfterTheTimeoutAndIsDismissedOnTheService() {
        val started = System.nanoTime()

        assertEquals(KeyStoreProblem.DISMISSED, problemOf { prompts.complete(bus, "/p/9") })

        assertTrue(System.nanoTime() - started >= 250_000_000L, "it waited for the timeout")
        assertEquals(listOf("watch /p/9", "start /p/9", "dismiss /p/9", "unwatch /p/9"), bus.calls)
    }

    @Test
    fun aLockWhileAPromptIsOpenAbandonsItAtOnce() {
        val slow = KeyringPrompts(timeoutMillis = 60_000, pollMillis = 10)
        var failure: SecretStoreException? = null
        val waiting = thread {
            failure = runCatching { slow.complete(bus, "/p/9") }.exceptionOrNull() as? SecretStoreException
        }
        val deadline = System.currentTimeMillis() + 5_000
        while ("start /p/9" !in bus.calls && System.currentTimeMillis() < deadline) Thread.sleep(5)

        slow.abandonPending()
        waiting.join(5_000)

        assertFalse(waiting.isAlive, "the wait ended long before its timeout")
        assertEquals(KeyStoreProblem.DISMISSED, failure?.problem)
        assertTrue("dismiss /p/9" in bus.calls)
    }

    @Test
    fun anAbandonBeforeAPromptStartsDoesNotEndTheNextOne() {
        prompts.abandonPending()
        bus.answers["/p/9"] = PromptOutcome(dismissed = false, result = "/o")

        assertEquals("/o", prompts.complete(bus, "/p/9")?.result)
    }
}
