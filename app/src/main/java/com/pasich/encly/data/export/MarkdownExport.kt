package com.pasich.encly.data.export

import com.pasich.encly.data.backup.VaultSnapshot
import com.pasich.encly.data.zip.ZipEntrySink
import com.pasich.encly.data.zip.zipWriter
import kotlinx.io.Sink

/**
 * The Markdown export of what a vault holds, written straight to a stream (a document the user
 * picked): nothing is buffered into a file, and nothing is added to the vault's data. Notes that
 * are in the trash are left out; archived notes go to `archive/`.
 *
 * It exports the vault that is open, whichever it is: the decoy vault exports what it contains.
 * Notes under a hidden tag are included, as they are in the vault.
 */
object MarkdownExport {
    const val TASKS_FILE = "tasks.md"
    const val ARCHIVE_FOLDER = "archive/"
    const val MIME_NOTE = "text/markdown"
    const val MIME_ZIP = "application/zip"

    /** Earliest time a zip entry can carry (the DOS epoch, 1980-01-01). */
    private const val ZIP_EPOCH_MILLIS = 315_532_800_000L

    /** Every note that is not in the trash and the tasks, as a zip of `.md` files. Closing [out] is the caller's. */
    fun writeAll(snapshot: VaultSnapshot, labels: MarkdownLabels, out: Sink) {
        writeAll(snapshot, labels, zipWriter(out))
    }

    internal fun writeAll(snapshot: VaultSnapshot, labels: MarkdownLabels, zip: ZipEntrySink) {
        val tagNames = snapshot.tags.associate { it.id to it.nameTag }
        val names = MarkdownFileNames(reserved = listOf(TASKS_FILE))
        zip.putText(TASKS_FILE, null, MarkdownTasks.render(snapshot.tasks, snapshot.subtasks, snapshot.tags, labels))
        snapshot.notes.filterNot { it.isTrash }.forEach { note ->
            val folder = if (note.isArchived) ARCHIVE_FOLDER else ""
            val name = names.next(note.title, labels.untitled, folder)
            zip.putText(name, note.date, MarkdownNotes.render(note, tagNames[note.tagId], labels))
        }
        zip.finish()
    }

    /** The note [noteId] as one `.md` document; false when the vault has no such note (or it is in the trash). */
    fun writeNote(snapshot: VaultSnapshot, noteId: Long, labels: MarkdownLabels, out: Sink): Boolean {
        val note = snapshot.notes.firstOrNull { it.id == noteId && !it.isTrash } ?: return false
        val tagName = snapshot.tags.firstOrNull { it.id == note.tagId }?.nameTag
        out.write(MarkdownNotes.render(note, tagName, labels).encodeToByteArray())
        return true
    }

    private fun ZipEntrySink.putText(name: String, time: Long?, text: String) =
        put(name, time?.takeIf { it >= ZIP_EPOCH_MILLIS }, text.encodeToByteArray())
}
