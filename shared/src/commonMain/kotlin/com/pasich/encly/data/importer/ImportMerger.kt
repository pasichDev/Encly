package com.pasich.encly.data.importer

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupTag
import com.pasich.encly.data.backup.BackupImporter
import com.pasich.encly.data.backup.ImportSummary
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteTag
import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Writes the notes the user chose into the open vault: [BackupImporter]'s MERGE with tags matched by
 * name ([com.pasich.encly.data.backup.TagMatch.UID_OR_NAME]), for a payload that has notes and tags only.
 * The result is the same; the difference is how it runs.
 *
 * - **All or nothing, by one transaction.** Everything happens inside a single
 *   [VaultDataStore.inTransaction]: tags first, then the notes in batches of [BATCH]. Nothing is
 *   committed until the last batch is in, so a failure, a cancel, a full disk or a killed process leaves
 *   the vault exactly as it was (SQLite throws an uncommitted transaction away the next time the
 *   database is opened). There is no staging area, no journal of what was written and no cleanup to
 *   run: both would need either a table the vault does not have or a file outside it.
 * - **Batches are units of work, not of commit.** The vault is read once ([VaultDataStore.snapshot], as
 *   the backup import does) and reduced at once to what the import needs, uids and tag ids. A batch is
 *   a window over the payload (no copy of it is made), and between batches the import checks that it
 *   has not been cancelled, so Cancel and a re-lock stop it at the next batch and roll back.
 *
 * The payload must already be validated ([ImportReview.selection] does that).
 */
internal object ImportMerger {
    const val BATCH = 200

    suspend fun merge(payload: BackupPayload, store: VaultDataStore, batchSize: Int = BATCH): ImportSummary {
        require(batchSize > 0) { "The batch size must be positive" }
        // Tasks are not merged here; a payload that carries some must fail, not lose them silently.
        require(payload.tasks.isEmpty()) { "An import payload has no tasks" }
        return store.inTransaction {
            val known = Known.of(store)
            val tagsAdded = addTags(payload.tags, known, store)
            val notesAdded = addNotes(payload.notes, known, store, batchSize)
            ImportSummary(
                notesAdded = notesAdded,
                tagsAdded = tagsAdded,
                tasksAdded = 0,
                skipped = payload.tags.size + payload.notes.size - tagsAdded - notesAdded,
            )
        }
    }

    /** What the vault holds that the import must not repeat or can reuse, kept small: no note text. */
    private class Known(
        val noteUids: Set<String>,
        /** Tag uid -> local id, for the vault's tags and, as they are added or matched, the import's. */
        val tagIds: MutableMap<String, Long>,
        /** Tag name key -> local id; the first tag with a name wins. */
        val tagIdsByName: MutableMap<String, Long>,
        var nextTagPosition: Int,
    ) {
        companion object {
            suspend fun of(store: VaultDataStore): Known {
                val vault = store.snapshot()
                val byName = HashMap<String, Long>()
                vault.tags.forEach { byName.getOrPut(BackupImporter.tagNameKey(it.nameTag)) { it.id } }
                return Known(
                    noteUids = vault.notes.mapTo(HashSet()) { it.uid },
                    tagIds = vault.tags.associate { it.uid to it.id }.toMutableMap(),
                    tagIdsByName = byName,
                    // Merged tags go after the ones already on this device, in their backup order.
                    nextTagPosition = vault.tags.maxOfOrNull { it.position + 1 } ?: 0,
                )
            }
        }
    }

    private suspend fun addTags(tags: List<BackupTag>, known: Known, store: VaultDataStore): Int =
        tags.sortedBy { it.position }.count { tag -> tag.uid !in known.tagIds && addUnlessSameName(tag, known, store) }

    /** Adds [tag], or maps it to the vault's tag of the same name; true if it was added. */
    private suspend fun addUnlessSameName(tag: BackupTag, known: Known, store: VaultDataStore): Boolean {
        val key = BackupImporter.tagNameKey(tag.name)
        val sameName = known.tagIdsByName[key]
        if (sameName != null) {
            known.tagIds[tag.uid] = sameName
        } else {
            val id = store.insertTag(
                Tag(nameTag = tag.name, isVisible = tag.visible, position = known.nextTagPosition++, uid = tag.uid),
            )
            known.tagIds[tag.uid] = id
            known.tagIdsByName[key] = id
        }
        return sameName == null
    }

    private suspend fun addNotes(notes: List<BackupNote>, known: Known, store: VaultDataStore, batchSize: Int): Int {
        var added = 0
        var from = 0
        while (from < notes.size) {
            currentCoroutineContext().ensureActive()
            for (note in notes.subList(from, minOf(from + batchSize, notes.size))) {
                if (note.uid in known.noteUids) continue
                val noteId = store.insertNote(note.toEntity())
                // Two backup tags can be one tag here (matched by name): each link is written once.
                for (tagId in note.tagUids.mapNotNull(known.tagIds::get).distinct()) {
                    store.insertNoteTag(NoteTag(noteId, tagId))
                }
                added++
            }
            from += batchSize
        }
        return added
    }

    private fun BackupNote.toEntity() = Note(
        title = title,
        value = value,
        description = description,
        date = date,
        dateCreate = dateCreate,
        isTrash = isTrash,
        uid = uid,
        // An archived note is never pinned, whatever the file says.
        isPinned = isPinned && !isArchived,
        isArchived = isArchived,
        color = color,
        textSize = textSize,
        lineSpacing = lineSpacing,
    )
}
