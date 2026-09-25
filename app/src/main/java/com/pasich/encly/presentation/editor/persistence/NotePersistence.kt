package com.pasich.encly.presentation.editor.persistence

import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.common.suspendRunCatching
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.hasContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
@Suppress("TooManyFunctions") // Every write of the note, so all of them share one mutex.
class NotePersistence(
    private val notesRepository: NotesRepository,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
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

    // False while a stored note (or the source of a copy) is still to be read ([loading]). Until
    // then the editor does not hold that note yet, so nothing may be saved: a save would see
    // id = -1 and insert a duplicate.
    @Volatile
    private var loaded = !loading

    // The title, tag and content last written to (or read from) the database. The autosave
    // only writes when the editor differs from it.
    private var persistedSnapshot: String = _state.value.note.let { snapshotOf(it.title, it.tagId, "[]") }

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

    /** False when nothing may be saved: closed, still loading, read-only or unreadable. */
    private val canPersist: Boolean
        get() = !closed && loaded && !readOnly && !_contentLoadFailed.value

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
        persistedSnapshot = snapshotOf(shown.title, shown.tagId, contentJson())
        openedSnapshot = persistedSnapshot
        loaded = true
        _status.value = SaveStatusNote.OLD
        // Unreadable content: the note (its title) is shown, but the editor stays read-only.
        return parsed != null
    }

    /**
     * Saves the note [AUTOSAVE_DEBOUNCE_MS] after the last change of [contentChanges], the
     * title or the tag, if the editor then differs from what is stored. Opening a note saves nothing, and
     * the first edit is saved like any other.
     */
    @OptIn(FlowPreview::class)
    fun startAutosave(scope: CoroutineScope, contentChanges: Flow<Unit>) {
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            combine(contentChanges, _state.map { it.note.title to it.note.tagId }.distinctUntilChanged()) { _, _ -> }
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { save(onlyIfChanged = true) }
        }
    }

    /** Changes the note's metadata (title, tag) in the editor; it is stored with the next save. */
    fun updateNote(transform: (Note) -> Note) {
        _state.value = _state.value.copy(note = transform(_state.value.note))
    }

    /**
     * Stores the note and returns once the database reports the result. A blank new note is
     * not stored; a save that is not allowed (see [canPersist]) is a successful no-op. With
     * [onlyIfChanged] (the autosave), nothing is written when the editor matches what is stored.
     */
    suspend fun save(onlyIfChanged: Boolean = false): Boolean = saveMutex.withLock {
        if (!canPersist) {
            if (_status.value != SaveStatusNote.LOADING && _status.value != SaveStatusNote.FAILED) {
                _status.value = SaveStatusNote.OLD
            }
            return@withLock true
        }
        val currentNote = _state.value.note
        if (currentNote.id == -1L && currentNote.title.isBlank() && blocks().none { it.hasContent() }) {
            return@withLock true
        }

        val blocksJson = contentJson()
        val snapshot = snapshotOf(currentNote.title, currentNote.tagId, blocksJson)
        if (onlyIfChanged && snapshot == persistedSnapshot) return@withLock true

        _status.value = SaveStatusNote.SAVING
        val saved = suspendRunCatching {
            if (currentNote.id != -1L) {
                val stored = withContext(ioDispatcher) {
                    notesRepository.updateStored(currentNote, _state.value.backupNote, blocksJson)
                }
                // The editor shows when the note was last edited.
                stored?.let { note -> updateNote { it.copy(date = note.date, description = note.description) } }
                stored != null
            } else {
                val insertedId = withContext(ioDispatcher) { notesRepository.insertDraft(currentNote, blocksJson) }
                // Merged into the latest state: a title or tag changed while the insert ran is
                // kept, and the next autosave stores it.
                insertedId?.let { id ->
                    updateNote { it.copy(id = id, value = blocksJson, date = System.currentTimeMillis()) }
                }
                insertedId != null
            }
        }.onFailure { AppLogger.e(TAG, "Note persistence failed", it) }.getOrDefault(false)

        if (saved) persistedSnapshot = snapshot
        _status.value = if (saved) SaveStatusNote.SAVED else SaveStatusNote.FAILED
        saved
    }

    /**
     * Puts the stored note back as it was when the editor opened it. A new note (or copy) that
     * was already autosaved is deleted instead of leaving a ghost note behind.
     */
    suspend fun discard(): Boolean = saveMutex.withLock {
        if (!loaded) return@withLock false
        val currentNote = _state.value.note
        val backupNote = _state.value.backupNote

        val discarded = when {
            backupNote.id != -1L -> withContext(ioDispatcher) { notesRepository.updateNote(backupNote) }
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
        if (!loaded || currentNote.id == -1L) return@withLock false

        val restored = currentNote.copy(value = contentJson(), isTrash = false)
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
        if (!loaded) return@withLock false
        if (currentNote.id == -1L) {
            close()
            return@withLock true
        }

        val trashed = currentNote.copy(value = contentJson(), isTrash = true)
        val ok = updateNoteTrashStatusUseCase.invoke(trashed, true).isSuccess
        if (ok) {
            // Keep the in-memory note in sync with the row: nothing may write isTrash=false back.
            _state.value = _state.value.copy(note = trashed)
            close()
        }
        ok
    }

    /** Whether the editor holds nothing to keep: a new note without a title or content. */
    val isBlankDraft: Boolean
        get() = _state.value.note.let { it.id == -1L && it.title.isBlank() } && blocks().none { it.hasContent() }

    /**
     * Whether the note differs from how the editor opened it (its title, tag or content), or a
     * new note was already stored: what "discard" would undo.
     */
    suspend fun hasChanges(): Boolean {
        val (note, backup) = _state.value.let { it.note to it.backupNote }
        if (note.id != backup.id) return true
        return note.title != backup.title || note.tagId != backup.tagId ||
            snapshotOf(note.title, note.tagId, contentJson()) != openedSnapshot
    }

    /** Stores a copy of the note, titled by [copyTitle]. Returns its id, or -1 on failure. */
    suspend fun duplicate(): Long = saveMutex.withLock {
        val currentNote = _state.value.note
        val content = contentJson()
        withContext(ioDispatcher) {
            notesRepository.insertNote(
                Note.new(title = copyTitle(currentNote.title), value = content, tagId = currentNote.tagId),
            )
        }.getOrNull()?.takeIf { it > 0L } ?: -1L
    }

    /** Deletes the note from the database for good. */
    suspend fun delete(): Boolean = saveMutex.withLock {
        val currentNoteId = _state.value.note.id
        val deleted = when {
            !loaded -> false
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

private fun snapshotOf(title: String, tagId: Long?, blocksJson: String): String = "$title\u0000$tagId\u0000$blocksJson"

/**
 * Stores [note] with [blocksJson] as its content; returns the stored note, or null. Its date
 * moves forward only when the title or content differ from [backup], the note as the editor
 * opened it, and then an imported summary ([Note.description]) no longer describes it.
 */
private suspend fun NotesRepository.updateStored(note: Note, backup: Note, blocksJson: String): Note? {
    val changed = backup.hasContentChanged(blocksJson, note.title)
    val stored = if (changed) {
        note.copy(value = blocksJson, date = System.currentTimeMillis(), description = "")
    } else {
        note.copy(value = blocksJson)
    }
    return stored.takeIf { updateNote(it).isSuccess }
}

/** A copy of the blocks holding their current values, to serialize away from the editor. */
private fun List<Block>.frozen(): List<Block> = map { block ->
    when (block) {
        is Block.TextBlock -> block.copy(text = MutableStateFlow(block.text.value))
        is Block.HBlock -> block.copy(text = MutableStateFlow(block.text.value))
        is Block.QuoteBlock -> block.copy(text = MutableStateFlow(block.text.value))
        is Block.LinkBlock -> block.copy(block = MutableStateFlow(block.block.value))
        is Block.ListBlock -> block.copy(items = MutableStateFlow(block.items.value))
        is Block.SeparatorBlock -> block
    }
}

/** Inserts [note] as a new row with [blocksJson] as its content; returns its id, or null. */
private suspend fun NotesRepository.insertDraft(note: Note, blocksJson: String): Long? =
    insertNote(Note.new(title = note.title, value = blocksJson, tagId = note.tagId))
        .getOrNull()
        ?.takeIf { it > 0L }

/**
 * [this] note as a new, unsaved copy (id = -1) titled [copyTitle]. "No tag" stays null: it has
 * exactly one representation.
 */
private fun Note.asCopy(copyTitle: String): Note = copy(
    id = -1,
    title = copyTitle,
    date = System.currentTimeMillis(),
    uid = "",
)
