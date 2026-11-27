package com.pasich.encly.data.importer

import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.model.Note
import com.pasich.encly.testutil.InMemoryVaultDataStore

/**
 * An in-memory vault that fails and is interrupted on demand, with the guarantee of the real one that the
 * import relies on: a transaction is kept only if its block and its commit both succeed, whatever is thrown.
 * (A Room transaction rolls back on any `Throwable`; [InMemoryVaultDataStore] restores on `RuntimeException`
 * only, which would prove nothing for an out-of-memory error or a simulated crash.)
 */
internal class FaultyVault(val vault: InMemoryVaultDataStore = InMemoryVaultDataStore()) : VaultDataStore by vault {
    /** Runs before the n-th note insert (1-based, across transactions); it may throw, or cancel the import. */
    var beforeNote: (Int) -> Unit = {}

    /** Thrown when the block of a transaction has finished, as a full disk is at COMMIT. */
    var failCommit: Throwable? = null

    private var notesInserted = 0

    // The bound store must be this one, not the delegate (the default would return the delegate itself).
    override fun bindToCurrentSession(): VaultDataStore = this

    override suspend fun insertNote(note: Note): Long {
        beforeNote(++notesInserted)
        return vault.insertNote(note)
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        val notes = vault.notes.toList()
        val tags = vault.tags.toList()
        val tasks = vault.tasks.toList()
        val subtasks = vault.subtasks.toList()
        val links = vault.noteTags.toList()
        try {
            // The block runs against this vault, so its inserts come back through insertNote above.
            val result = block()
            failCommit?.let { throw it }
            return result
        } catch (t: Throwable) {
            vault.notes.reset(notes)
            vault.tags.reset(tags)
            vault.tasks.reset(tasks)
            vault.subtasks.reset(subtasks)
            vault.noteTags.reset(links)
            throw t
        }
    }

    private fun <T> MutableList<T>.reset(to: List<T>) {
        clear()
        addAll(to)
    }
}
