package com.pasich.encly.data.export

import com.pasich.encly.data.backup.VaultSnapshot
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    fun writeAll(snapshot: VaultSnapshot, labels: MarkdownLabels, out: OutputStream) {
        val zip = ZipOutputStream(out)
        val tagNames = snapshot.tags.associate { it.id to it.nameTag }
        val names = MarkdownFileNames(reserved = listOf(TASKS_FILE))
        zip.put(TASKS_FILE, null, MarkdownTasks.render(snapshot.tasks, snapshot.subtasks, snapshot.tags, labels))
        snapshot.notes.filterNot { it.isTrash }.forEach { note ->
            val folder = if (note.isArchived) ARCHIVE_FOLDER else ""
            val name = names.next(note.title, labels.untitled, folder)
            zip.put(name, note.date, MarkdownNotes.render(note, tagNames[note.tagId], labels))
        }
        zip.finish()
    }

    /** The note [noteId] as one `.md` document; false when the vault has no such note (or it is in the trash). */
    fun writeNote(snapshot: VaultSnapshot, noteId: Long, labels: MarkdownLabels, out: OutputStream): Boolean {
        val note = snapshot.notes.firstOrNull { it.id == noteId && !it.isTrash } ?: return false
        val tagName = snapshot.tags.firstOrNull { it.id == note.tagId }?.nameTag
        out.write(MarkdownNotes.render(note, tagName, labels).toByteArray(Charsets.UTF_8))
        return true
    }

    /** The name the picker suggests for a single note: its title, made safe. */
    fun noteFileName(title: String, labels: MarkdownLabels): String = MarkdownFileNames().next(title, labels.untitled)

    private fun ZipOutputStream.put(name: String, time: Long?, text: String) {
        val entry = ZipEntry(name)
        if (time != null && time >= ZIP_EPOCH_MILLIS) entry.time = time
        putNextEntry(entry)
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
