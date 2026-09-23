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
    private var persistedSnapshot: String = currentSnapshot

    private var autosaveJob: Job? = null

    private val currentSnapshot: String
        get() = _state.value.note.let { snapshotOf(it.title, it.tagId, BlockConverter.blocksToJson(blocks())) }

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
        persistedSnapshot = currentSnapshot
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
            combine(contentChanges, _state.map { it.note.title to it.note.tagId }) { _, _ -> }
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { if (currentSnapshot != persistedSnapshot) save() }
        }
    }

    /** Changes the note's metadata (title, tag) in the editor; it is stored with the next save. */
    fun updateNote(transform: (Note) -> Note) {
        _state.value = _state.value.copy(note = transform(_state.value.note))
    }

    /**
     * Stores the note and returns once the database reports the result. A blank new note is
     * not stored; a save that is not allowed (see [canPersist]) is a successful no-op.
     */
    suspend fun save(): Boolean = saveMutex.withLock {
        if (!canPersist) {
            if (_status.value != SaveStatusNote.LOADING) _status.value = SaveStatusNote.OLD
            return@withLock true
        }
        val currentNote = _state.value.note
        val content = blocks()
        if (currentNote.id == -1L && currentNote.title.isBlank() && content.none { it.hasContent() }) {
            _status.value = SaveStatusNote.OLD
            return@withLock true
        }

        _status.value = SaveStatusNote.SAVING
        val blocksJson = BlockConverter.blocksToJson(content)
        val saved = suspendRunCatching {
            if (currentNote.id != -1L) {
                val backup = _state.value.backupNote
                withContext(ioDispatcher) { notesRepository.updateStored(currentNote, backup, blocksJson) }
            } else {
                val insertedId = withContext(ioDispatcher) { notesRepository.insertDraft(currentNote, blocksJson) }
                // Merged into the latest state: a title or tag changed while the insert ran is
                // kept, and the next autosave stores it.
                insertedId?.let { id -> updateNote { it.copy(id = id, value = blocksJson) } }
                insertedId != null
            }
        }.onFailure { AppLogger.e(TAG, "Note persistence failed", it) }.getOrDefault(false)

        if (saved) persistedSnapshot = snapshotOf(currentNote.title, currentNote.tagId, blocksJson)
        _status.value = if (saved) SaveStatusNote.SAVED else SaveStatusNote.OLD
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

        val restored = currentNote.copy(value = BlockConverter.blocksToJson(blocks()), isTrash = false)
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

        val trashed = currentNote.copy(value = BlockConverter.blocksToJson(blocks()), isTrash = true)
        val ok = updateNoteTrashStatusUseCase.invoke(trashed, true).isSuccess
        if (ok) {
            // Keep the in-memory note in sync with the row: nothing may write isTrash=false back.
            _state.value = _state.value.copy(note = trashed)
            close()
        }
        ok
    }

    /** Stores a copy of the note, titled by [copyTitle]. Returns its id, or -1 on failure. */
    suspend fun duplicate(): Long = saveMutex.withLock {
        val currentNote = _state.value.note
        notesRepository.insertNote(
            Note.new(
                title = copyTitle(currentNote.title),
                value = BlockConverter.blocksToJson(blocks()),
                tagId = currentNote.tagId,
            ),
        ).getOrNull()?.takeIf { it > 0L } ?: -1L
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
 * Stores [note] with [blocksJson] as its content. Its date moves forward only when the title or
 * content differ from [backup], the note as the editor opened it.
 */
private suspend fun NotesRepository.updateStored(note: Note, backup: Note, blocksJson: String): Boolean {
    val date = if (backup.hasContentChanged(blocksJson, note.title)) System.currentTimeMillis() else note.date
    return updateNote(note.copy(value = blocksJson, date = date)).isSuccess
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
