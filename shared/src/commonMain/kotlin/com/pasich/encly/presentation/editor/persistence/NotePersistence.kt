package com.pasich.encly.presentation.editor.persistence

import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.common.suspendRunCatching
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.hasContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.Clock

enum class SaveStatusNote {
    OLD,
    SAVING,
    SAVED,
    LOADING,

    /** The last save failed: the editor still holds changes the database does not. */
    FAILED,
}

data class LoadNoteState(val note: Note = Note(id = -1), val backupNote: Note = Note(id = -1))

/** Quiet time after the last edit before the note is autosaved. */
const val AUTOSAVE_DEBOUNCE_MS = 2000L

private const val TAG = "NotePersistence"

/**
 * Reads and writes the note an editor works on: load, the debounced autosave, explicit saves,
 * discard, trash/restore, duplicate and delete. [blocks] is the editor's current content.
 *
 * Every write runs under one mutex, so autosave, an explicit exit-save and the last-chance save
 * of a closing screen never interleave. Once the editor is [close]d (after trash, restore,
 * delete or discard, or an explicit exit), no queued or later save writes the note again, so it
 * cannot bring a trashed note back or write edits over a discard.
 */
@Suppress("TooManyFunctions", "LongParameterList") // Every write of the note shares one mutex and its use cases.
class NotePersistence(
    private val notesRepository: NotesRepository,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val updateNoteArchiveStatusUseCase: UpdateNoteArchiveStatusUseCase,
    private val ioDispatcher: CoroutineDispatcher,
    private val readOnly: Boolean,
    private val blocks: () -> List<Block>,
    private val copyTitle: NoteCopyTitle,
    loading: Boolean = false,
) {
    private val _state = MutableStateFlow(LoadNoteState())
    val state: StateFlow<LoadNoteState> = _state.asStateFlow()

    private val _status = MutableStateFlow(SaveStatusNote.OLD)
    val status: StateFlow<SaveStatusNote> = _status.asStateFlow()

    // True when a note had stored content that could not be read. Nothing may then be saved,
    // so an edit cannot overwrite the unreadable original.
    private val _contentLoadFailed = MutableStateFlow(false)
    val contentLoadFailed: StateFlow<Boolean> = _contentLoadFailed.asStateFlow()

    private val saveMutex = Mutex()

    @Volatile
    private var closed = false

    /**
     * Set while another window edits this note: nothing of this editor may be written, so the other window's
     * work is not overwritten with what this one loaded earlier. Unlike [closed], it can be lifted.
     */
    @Volatile
    var suspended = false

    // False while a stored note (or the source of a copy) is still to be read ([loading]). Until
    // then the editor does not hold that note yet, so nothing may be saved: a save would see
    // id = -1 and insert a duplicate.
    @Volatile
    private var loaded = !loading

    // The title, tag and content last written to (or read from) the database. The autosave
    // only writes when the editor differs from it.
    private var persistedSnapshot: String = snapshotOf(_state.value.note, "[]")

    // The row as this editor last read or wrote it: a save writes only the columns that differ from it, so it never
    // puts back a column that another window changed meanwhile (trash, archive, pin, colour).
    private var persistedNote: Note = _state.value.note

    // The note as the editor opened it, for [hasChanges].
    private var openedSnapshot: String = persistedSnapshot

    private var autosaveJob: Job? = null

    /**
     * The editor's content as stored JSON. The blocks' values are copied where this is called
     * (cheap), and serialized on [ioDispatcher], so a long note never serializes on the main thread.
     */
    private suspend fun contentJson(): String {
        val content = blocks().frozen()
        return withContext(ioDispatcher) { BlockConverter.blocksToJson(content) }
    }

    /**
     * What a write that is not an edit (archive, duplicate) stores as the content: the editor's blocks, or the stored
     * text as it is when it could not be read, so that a note this version cannot read is never written back empty.
     */
    private suspend fun contentToKeep(note: Note): String = if (_contentLoadFailed.value) note.value else contentJson()

    /** False when nothing may be saved: closed, still loading, read-only or unreadable. */
    private val canPersist: Boolean
        get() = !closed && loaded && !readOnly && !suspended && !_contentLoadFailed.value

    /**
     * Reads note [noteId] (as a new, unsaved copy with [isCopy]) and hands its blocks to
     * [showBlocks]. Returns false when the note does not exist or could not be read; the
     * editor must then stay read-only.
     */
    suspend fun load(noteId: Long, isCopy: Boolean, showBlocks: (List<Block>) -> Unit): Boolean {
        _status.value = SaveStatusNote.LOADING
        val note = suspendRunCatching { notesRepository.getNoteById(noteId) }
            .onFailure { AppLogger.e(TAG, "Error loading note", it) }
            .getOrNull()
        if (note == null) {
            _contentLoadFailed.value = true
            _status.value = SaveStatusNote.OLD
            return false
        }

        // A valid title-only note ("[]", or no content) keeps the editor's blank block.
        val parsed = if (note.value.isEmpty()) {
            emptyList()
        } else {
            withContext(ioDispatcher) { BlockConverter.jsonToBlocksOrNull(note.value) }
        }
        if (parsed == null) {
            _contentLoadFailed.value = true
            AppLogger.e(TAG, "Stored note content could not be parsed")
        }
        // The blocks and the note (its id) are published together, so no save can ever see the
        // note's content without its id.
        parsed?.takeIf { it.isNotEmpty() }?.let(showBlocks)
        val shown = if (isCopy) note.asCopy(copyTitle(note.title)) else note
        _state.value = LoadNoteState(note = shown, backupNote = shown)
        persistedSnapshot = snapshotOf(shown, contentJson())
        persistedNote = shown
        openedSnapshot = persistedSnapshot
        loaded = true
        _status.value = SaveStatusNote.OLD
        // Unreadable content: the note (its title) is shown, but the editor stays read-only.
        return parsed != null
    }

    /**
     * Saves the note [AUTOSAVE_DEBOUNCE_MS] after the last change of [contentChanges], the
     * title, tag, pin or colour, if the editor then differs from what is stored. Opening a note saves nothing, and
     * the first edit is saved like any other.
     */
    @OptIn(FlowPreview::class)
    fun startAutosave(scope: CoroutineScope, contentChanges: Flow<Unit>) {
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            combine(contentChanges, _state.map { it.note.metaKey() }.distinctUntilChanged()) { _, _ -> }
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { save(onlyIfChanged = true) }
        }
    }

    /** Changes the note's metadata (title, tag, pin, colour) in the editor; it is stored with the next save. */
    fun updateNote(transform: (Note) -> Note) {
        _state.value = _state.value.copy(note = transform(_state.value.note))
    }

    /**
     * Stores the note and returns once the database reports the result. A blank new note is
     * not stored; a save that is not allowed (see [canPersist]) is a successful no-op. With
     * [onlyIfChanged] (the autosave), nothing is written when the editor matches what is stored.
     */
    suspend fun save(onlyIfChanged: Boolean = false): Boolean = saveMutex.withLock { saveLocked(onlyIfChanged) }

    /** [save] for a caller that holds [saveMutex] already. */
    private suspend fun saveLocked(onlyIfChanged: Boolean): Boolean = when {
        !canPersist -> {
            if (_status.value != SaveStatusNote.LOADING && _status.value != SaveStatusNote.FAILED) {
                _status.value = SaveStatusNote.OLD
            }
            true
        }

        // A blank new note is not stored.
        isBlankDraft -> true

        else -> writeNote(onlyIfChanged)
    }

    private suspend fun writeNote(onlyIfChanged: Boolean): Boolean {
        val currentNote = _state.value.note
        val blocksJson = contentJson()
        val snapshot = snapshotOf(currentNote, blocksJson)
        if (onlyIfChanged && snapshot == persistedSnapshot) return true

        _status.value = SaveStatusNote.SAVING
        val saved = suspendRunCatching {
            if (currentNote.id != -1L) {
                val stored = withContext(ioDispatcher) {
                    notesRepository.updateStored(currentNote, persistedNote, _state.value.backupNote, blocksJson)
                }
                // The editor shows when the note was last edited.
                stored?.let { note ->
                    updateNote { it.copy(date = note.date, description = note.description) }
                    persistedNote = note
                }
                stored != null
            } else {
                val draft = newDraft(currentNote, blocksJson)
                val insertedId = withContext(ioDispatcher) { notesRepository.insertDraft(draft) }
                // Merged into the latest state: a title or tag changed while the insert ran is
                // kept, and the next autosave stores it. The stored creation time comes along:
                // every later save writes the whole row, so a note that still said 0 here would
                // wipe it on the first autosave.
                insertedId?.let { id ->
                    updateNote {
                        it.copy(id = id, value = blocksJson, date = draft.date, dateCreate = draft.dateCreate)
                    }
                    persistedNote = draft.copy(id = id)
                }
                insertedId != null
            }
        }.onFailure { AppLogger.e(TAG, "Note persistence failed", it) }.getOrDefault(false)

        if (saved) persistedSnapshot = snapshot
        _status.value = if (saved) SaveStatusNote.SAVED else SaveStatusNote.FAILED
        return saved
    }

    /**
     * Ends this editor's hold on the note (its window gives the claim back): the changes not yet stored are saved
     * first, then nothing is written until [resume]. Both happen under the save mutex, so no autosave slips in
     * between, and the claim is released only after this returns: another window that claims the note next loads
     * what was saved here.
     */
    suspend fun release(): Boolean = saveMutex.withLock {
        val saved = saveLocked(onlyIfChanged = true)
        suspended = true
        saved
    }

    /**
     * Starts editing again after [release], or after another window had the note. If the stored note changed
     * meanwhile it is read again and shown by [showBlocks]; otherwise the editor keeps what it holds (and its undo
     * history).
     */
    suspend fun resume(showBlocks: (List<Block>) -> Unit) = saveMutex.withLock {
        val id = _state.value.note.id
        if (id != -1L && loaded) {
            val stored = suspendRunCatching { notesRepository.getNoteById(id) }.getOrNull()
            if (stored != null) showIfChanged(stored, showBlocks)
        }
        suspended = false
    }

    /** The stored row of the note, following every change to it; empty for a note that is not stored yet. */
    fun storedNote(): Flow<Note?> {
        val id = _state.value.note.id
        // The flow fails once the vault closes; the view then simply stops following.
        return if (id == -1L) emptyFlow() else notesRepository.observeNote(id).map { it?.note }.catch { }
    }

    /** A read-only view of a note edited elsewhere: shows [stored] when it differs from what is on screen. */
    suspend fun showStored(stored: Note?, showBlocks: (List<Block>) -> Unit) {
        if (stored == null) return
        saveMutex.withLock { if (suspended) showIfChanged(stored, showBlocks) }
    }

    private suspend fun showIfChanged(stored: Note, showBlocks: (List<Block>) -> Unit) {
        if (closed || stored.sameAs(persistedNote)) return
        val parsed = if (stored.value.isEmpty()) {
            emptyList()
        } else {
            withContext(ioDispatcher) { BlockConverter.jsonToBlocksOrNull(stored.value) }
        }
        // Unreadable content stays as it is on screen; the original is never replaced.
        if (parsed == null) return
        showBlocks(parsed)
        _state.value = LoadNoteState(note = stored, backupNote = stored)
        persistedSnapshot = snapshotOf(stored, contentJson())
        persistedNote = stored
        openedSnapshot = persistedSnapshot
    }

    /**
     * Puts the stored note back as it was when the editor opened it. A new note (or copy) that
     * was already autosaved is deleted instead of leaving a ghost note behind.
     */
    suspend fun discard(): Boolean = saveMutex.withLock {
        if (!loaded || suspended) return@withLock false
        val currentNote = _state.value.note
        val backupNote = _state.value.backupNote

        val discarded = when {
            // Only the columns this editor changed go back; the trash and archive state never.
            backupNote.id != -1L -> withContext(ioDispatcher) {
                notesRepository.updateEdited(backupNote, persistedNote)
            }

            currentNote.id != -1L -> withContext(ioDispatcher) { notesRepository.deleteNoteById(currentNote.id) }

            else -> Result.success(Unit)
        }.isSuccess

        if (discarded) close()
        _status.value = SaveStatusNote.OLD
        discarded
    }

    /** Takes the note out of the trash, with the editor's content. */
    suspend fun restoreFromTrash(): Boolean = saveMutex.withLock {
        val currentNote = _state.value.note
        if (!loaded || suspended || currentNote.id == -1L) return@withLock false

        if (!saveLocked(onlyIfChanged = true)) return@withLock false
        val restored = _state.value.note.copy(isTrash = false)
        val ok = updateNoteTrashStatusUseCase.invoke(restored, false).isSuccess
        if (ok) {
            _state.value = _state.value.copy(note = restored)
            close()
        }
        ok
    }

    /** Moves the note to the trash (a soft delete), with the editor's content. */
    suspend fun moveToTrash(): Boolean = saveMutex.withLock {
        val currentNote = _state.value.note
        if (!loaded || suspended) return@withLock false
        if (currentNote.id == -1L) {
            close()
            return@withLock true
        }

        // What was typed goes first, then the note moves; the trash write touches its own two columns only.
        if (!saveLocked(onlyIfChanged = true)) return@withLock false
        val trashed = _state.value.note.copy(isTrash = true)
        val ok = updateNoteTrashStatusUseCase.invoke(trashed, true).isSuccess
        if (ok) {
            // Keep the in-memory note in sync with the row: nothing may write isTrash=false back.
            _state.value = _state.value.copy(note = trashed)
            close()
        }
        ok
    }

    /**
     * Moves the note to the archive (unpinning it) or back to the list, with the editor's
     * content. The editor is closed afterwards, like [moveToTrash].
     */
    suspend fun setArchived(archived: Boolean): Boolean = saveMutex.withLock {
        val currentNote = _state.value.note
        if (!loaded || suspended || currentNote.id == -1L) return@withLock false

        val updated = currentNote.copy(
            value = contentToKeep(currentNote),
            isArchived = archived,
            isPinned = currentNote.isPinned && !archived,
        )
        val ok = updateNoteArchiveStatusUseCase.invoke(updated, archived).isSuccess
        if (ok) {
            _state.value = _state.value.copy(note = updated)
            close()
        }
        ok
    }

    /** Whether the editor holds nothing to keep: a new note without a title or content. */
    val isBlankDraft: Boolean
        get() = _state.value.note.let { it.id == -1L && it.title.isBlank() } &&
            blocks().none { it.hasContent() || it is Block.CodeBlock || it is Block.TableBlock }

    /**
     * Whether the note differs from how the editor opened it (its title, tag or content), or a
     * new note was already stored: what "discard" would undo.
     */
    suspend fun hasChanges(): Boolean {
        val (note, backup) = _state.value.let { it.note to it.backupNote }
        if (note.id != backup.id) return true
        return note.title != backup.title || note.tagIds != backup.tagIds ||
            note.isPinned != backup.isPinned || note.color != backup.color ||
            note.textSize != backup.textSize || note.lineSpacing != backup.lineSpacing ||
            snapshotOf(note, contentJson()) != openedSnapshot
    }

    /** Stores a copy of the note, titled by [copyTitle]. Returns its id, or -1 on failure. */
    suspend fun duplicate(): Long = saveMutex.withLock {
        val currentNote = _state.value.note
        val content = contentToKeep(currentNote)
        withContext(ioDispatcher) {
            notesRepository.insertNote(
                Note.new(title = copyTitle(currentNote.title), value = content, tagIds = currentNote.tagIds)
                    .copy(
                        color = currentNote.color,
                        textSize = currentNote.textSize,
                        lineSpacing = currentNote.lineSpacing,
                    ),
            )
        }.getOrNull()?.takeIf { it > 0L } ?: -1L
    }

    /** Deletes the note from the database for good. */
    suspend fun delete(): Boolean = saveMutex.withLock {
        val currentNoteId = _state.value.note.id
        val deleted = when {
            !loaded || suspended -> false
            currentNoteId == -1L -> true
            else -> notesRepository.deleteNoteById(currentNoteId).isSuccess
        }
        if (deleted) close()
        deleted
    }

    /** Ends editing: no queued or later save may write this note again. */
    fun close() {
        closed = true
        autosaveJob?.cancel()
    }
}

private fun snapshotOf(note: Note, blocksJson: String): String =
    "${note.title}\u0000${note.tagIds.sorted()}\u0000${note.isPinned}\u0000${note.color}\u0000" +
        "${note.textSize}\u0000${note.lineSpacing}\u0000$blocksJson"

/** What besides the content makes the editor differ from the stored note; the autosave watches it. */
private data class NoteMeta(
    val title: String,
    val tagIds: Set<Long>,
    val isPinned: Boolean,
    val color: String,
    val textSize: Int?,
    val lineSpacing: Int?,
)

/** Whether [other] holds the same title, content and organizing columns (the editor's, not the trash state). */
private fun Note.sameAs(other: Note): Boolean = id == other.id && title == other.title && value == other.value &&
    tagIds == other.tagIds && isPinned == other.isPinned && color == other.color &&
    textSize == other.textSize && lineSpacing == other.lineSpacing && date == other.date

private fun Note.metaKey() = NoteMeta(title, tagIds, isPinned, color, textSize, lineSpacing)

/**
 * Stores [note] with [blocksJson] as its content, writing only what differs from [base] (the row as the editor last
 * saw it); returns the stored note, or null. Its date
 * moves forward only when the title or content differ from [backup], the note as the editor
 * opened it, and then an imported summary ([Note.description]) no longer describes it.
 */
private suspend fun NotesRepository.updateStored(note: Note, base: Note, backup: Note, blocksJson: String): Note? {
    val changed = backup.hasContentChanged(blocksJson, note.title)
    val stored = if (changed) {
        note.copy(value = blocksJson, date = Clock.System.now().toEpochMilliseconds(), description = "")
    } else {
        note.copy(value = blocksJson)
    }
    return stored.takeIf { updateEdited(it, base).isSuccess }
}

/** A copy of the blocks holding their current values, to serialize away from the editor. */
private fun List<Block>.frozen(): List<Block> = map { block ->
    when (block) {
        is Block.TextBlock -> block.copy(text = MutableStateFlow(block.text.value))

        is Block.HBlock -> block.copy(text = MutableStateFlow(block.text.value))

        is Block.QuoteBlock -> block.copy(text = MutableStateFlow(block.text.value))

        is Block.LinkBlock -> block.copy(block = MutableStateFlow(block.block.value))

        is Block.ListBlock -> block.copy(
            items = MutableStateFlow(block.items.value),
            locked = MutableStateFlow(block.locked.value),
        )

        is Block.CodeBlock -> block.copy(text = MutableStateFlow(block.text.value))

        is Block.TableBlock -> block.copy(table = MutableStateFlow(block.table.value))

        is Block.SeparatorBlock -> block
    }
}

/** The row a new note is first stored as: stamped with its creation time, which is never set again. */
private fun newDraft(note: Note, blocksJson: String): Note =
    Note.new(title = note.title, value = blocksJson, tagIds = note.tagIds)
        .copy(isPinned = note.isPinned, color = note.color, textSize = note.textSize, lineSpacing = note.lineSpacing)

/** Inserts [draft] as a new row; returns its id, or null. */
private suspend fun NotesRepository.insertDraft(draft: Note): Long? = insertNote(draft)
    .getOrNull()
    ?.takeIf { it > 0L }

/**
 * [this] note as a new, unsaved copy (id = -1) titled [copyTitle]. The copy keeps the tags and the colour but
 * starts unpinned and unarchived.
 */
private fun Note.asCopy(copyTitle: String): Note = copy(
    id = -1,
    title = copyTitle,
    date = Clock.System.now().toEpochMilliseconds(),
    uid = "",
    isPinned = false,
    isArchived = false,
)
