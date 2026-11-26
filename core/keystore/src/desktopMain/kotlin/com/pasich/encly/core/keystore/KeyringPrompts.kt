package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.KeyStoreProblem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * The few calls the keyring logic makes on the Secret Service of one connection; the real one speaks D-Bus
 * ([LinuxSecretServiceStore]), a test plays a service of its own. Object paths are plain strings; "no object" (the
 * spec's `/`) is null.
 */
internal interface KeyringBus {
    /** The default collection (`ReadAlias("default")`), or null when there is none. */
    fun defaultCollection(): String?

    /** Whether [collection] is locked now. */
    fun isLocked(collection: String): Boolean

    /** `Unlock` of [objects]: the prompt that has to be run for it, or null when none is needed. */
    fun unlock(objects: List<String>): String?

    /** `CreateCollection` with the alias `default`: what was made and the prompt that has to be run for it, if any. */
    fun createDefaultCollection(): CreatedCollection

    /** Listens for the end of [prompt] until the returned handle is closed; must be set before [startPrompt]. */
    fun watchPrompt(prompt: String, completed: (PromptOutcome) -> Unit): AutoCloseable

    /** `Prompt.Prompt` with no window id (the service shows its dialog on its own). Does not wait. */
    fun startPrompt(prompt: String)

    /** `Prompt.Dismiss`; never throws. */
    fun dismissPrompt(prompt: String)
}

/** What `CreateCollection` answered: the [collection] (null if the prompt makes it) and the [prompt] (null if none). */
internal class CreatedCollection(val collection: String?, val prompt: String?)

/** How a prompt ended: [dismissed] by the user, else the object path it gave ([result], when it gave one). */
internal class PromptOutcome(val dismissed: Boolean, val result: String?)

/**
 * What the Secret Service wants of the user before the keyring can be used, and the user's answer: unlocking the
 * default collection, creating it, running the prompts those return. A prompt is run as the specification says
 * (`Prompt.Prompt`, then the `Completed` signal) and waited for at most [timeoutMillis], on the calling thread (never
 * the UI's: the unlock and the setup run on a worker) and for as long as nobody calls [abandonPending]: a lock of the
 * vault does, and the wait ends at once, with the prompt dismissed on the service.
 */
internal class KeyringPrompts(
    private val timeoutMillis: Long = PROMPT_TIMEOUT_MILLIS,
    private val pollMillis: Long = POLL_MILLIS,
) {
    private val abandonCount = AtomicLong(0L)

    /** Ends every prompt wait that is running now; one that starts later is not affected. Any thread. */
    fun abandonPending() {
        abandonCount.incrementAndGet()
    }

    /**
     * The default collection, unlocked. A missing one is made first ([createMissing], which prompts) or reported as
     * null; a locked one is unlocked, with the service's own prompt. Fails with an unavailable
     * [SecretStoreException] when the user does not do what is asked.
     */
    fun openDefaultCollection(bus: KeyringBus, createMissing: Boolean): String? {
        val collection = bus.defaultCollection() ?: if (createMissing) createDefaultCollection(bus) else return null
        if (bus.isLocked(collection)) {
            complete(bus, bus.unlock(listOf(collection)))
            if (bus.isLocked(collection)) throw unavailable("The keyring is still locked", KeyStoreProblem.LOCKED)
        }
        return collection
    }

    /** Makes the default collection, running the prompt the service asks for. */
    private fun createDefaultCollection(bus: KeyringBus): String {
        val created = bus.createDefaultCollection()
        val outcome = complete(bus, created.prompt)
        return created.collection ?: outcome?.result
            ?: throw unavailable("The Secret Service made no default collection", KeyStoreProblem.NO_DEFAULT)
    }

    /** Unlocks [objects] (items found locked), running the prompt it needs; fails when they stay locked. */
    fun unlockAll(bus: KeyringBus, objects: List<String>) {
        complete(bus, bus.unlock(objects))
    }

    /** Runs [prompt], if there is one, to its end; null when there was none. */
    fun complete(bus: KeyringBus, prompt: String?): PromptOutcome? {
        if (prompt == null) return null
        val startedAt = abandonCount.get()
        val answer = CompletableFuture<PromptOutcome>()
        bus.watchPrompt(prompt) { answer.complete(it) }.use {
            bus.startPrompt(prompt)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (!answer.isDone) {
                val end = when {
                    abandonCount.get() != startedAt -> "The keyring prompt was abandoned"
                    System.nanoTime() >= deadline -> "The keyring prompt was not answered in time"
                    else -> null
                }
                if (end != null) {
                    bus.dismissPrompt(prompt)
                    throw unavailable(end, KeyStoreProblem.DISMISSED)
                }
                try {
                    answer.get(pollMillis, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    // Not answered yet: the next turn looks at the lock and the clock again.
                }
            }
        }
        val finished = answer.get()
        if (finished.dismissed) throw unavailable("The keyring prompt was dismissed", KeyStoreProblem.DISMISSED)
        return finished
    }

    companion object {
        /** The user types a keyring password in the service's dialog: minutes, not seconds. */
        const val PROMPT_TIMEOUT_MILLIS = 120_000L
        private const val POLL_MILLIS = 100L
    }
}

/** A keyring that is not usable right now ([problem] says why): the failure is transient, and never "lost". */
internal fun unavailable(message: String, problem: KeyStoreProblem, cause: Throwable? = null) = SecretStoreException(
    lost = false,
    message = message,
    cause = SecretStoreUnavailableException(message, cause),
    problem = problem,
)
