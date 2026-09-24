package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.di.ApplicationScope
import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.TextualBlock
import com.pasich.encly.dynamicBlocks.factory.BlockFactory
import com.pasich.encly.presentation.editor.persistence.LoadNoteState
import com.pasich.encly.presentation.editor.persistence.NoteCopyTitle
import com.pasich.encly.presentation.editor.persistence.NotePersistence
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.editor.state.BlockEditorState
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.editor.state.FocusRequest
import com.pasich.encly.presentation.editor.state.addBlockToEnd
import com.pasich.encly.presentation.editor.state.applyTool
import com.pasich.encly.presentation.editor.state.canMoveInteracted
import com.pasich.encly.presentation.editor.state.canRemoveInteracted
import com.pasich.encly.presentation.editor.state.focusFirstBlock
import com.pasich.encly.presentation.editor.state.focusWorkingBlock
import com.pasich.encly.presentation.editor.state.interactedBlockPosition
import com.pasich.encly.presentation.editor.state.moveInteracted
import com.pasich.encly.presentation.editor.state.removeInteracted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The note editor. Wires the block content ([BlockEditorState]) to the stored note
 * ([NotePersistence]) and decides whether the note may be edited at all (locked, or read-only
 * from the trash). Focus is plain state here; the UI owns the FocusRequesters.
 */
@HiltViewModel
class EditNoteViewModel
@Suppress("LongParameterList") // Hilt-injected dependencies; grouping them would only obscure wiring.
@Inject
constructor(
    notesRepository: NotesRepository,
    private val savedStateHandle: SavedStateHandle,
    updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    copyTitle: NoteCopyTitle,
) : ViewModel() {
    private val noteId: Long = savedStateHandle["idNote"]
        ?: -1 // Note identifier; when creating a new note it will be -1
    internal val copySource: Long =
        savedStateHandle["copySource"] ?: -1 // Identifier of the note to copy, if specified
    val isReadTrashOnly: Boolean =
        savedStateHandle["isReadTrashOnly"] as? Boolean == true // Flag for reading a note only from the trash
    internal val addTag: Long = savedStateHandle["addTag"] ?: 0

    /** A note being written from scratch (not opened, not a copy): the editor focuses its title. */
    val isNewNote: Boolean get() = noteId == -1L && copySource == -1L

    private val editor = BlockEditorState()

    private val persistence = NotePersistence(
        notesRepository = notesRepository,
        updateNoteTrashStatusUseCase = updateNoteTrashStatusUseCase,
        ioDispatcher = ioDispatcher,
        readOnly = isReadTrashOnly,
        blocks = { editor.blocks },
        copyTitle = copyTitle,
        loading = noteId != -1L || copySource != -1L,
    )

    val state: StateFlow<LoadNoteState> get() = persistence.state
    val status: StateFlow<SaveStatusNote> get() = persistence.status

    /**
     * True when a note had stored content that could not be read: the editor warns instead of
     * showing a silent blank screen, and nothing is saved over the original.
     */
    val contentLoadFailed: StateFlow<Boolean> get() = persistence.contentLoadFailed

    /** The blocks to display and edit. */
    val blocks: List<Block> get() = editor.blocks

    val canUndo: StateFlow<Boolean> get() = editor.canUndo
    val canRedo: StateFlow<Boolean> get() = editor.canRedo

    /** The block the user last worked on; the toolbar acts on it. */
    val interactedBlockId: StateFlow<String?> get() = editor.selection.interactedBlockId

    /** Position of the block the user last worked on, always inside [blocks]. */
    val interactedIndex: Int get() = editor.selection.interactedIndex

    /** Which block the editor UI should focus next. */
    val focusRequests: Flow<FocusRequest> get() = editor.selection.requests

    private val _lockEditor = MutableStateFlow(false)
    val lockEditor: StateFlow<Boolean> get() = _lockEditor

    // Link blocks whose saved address is open in URL entry. Editor UI state only: the block
    // keeps its stored link until a new one is committed.
    private val _editingLinkIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the link blocks the user is editing ([editLink]); they show URL entry. */
    val editingLinkIds: StateFlow<Set<String>> get() = _editingLinkIds

    val fontSize: StateFlow<Int> = settingsRepository.fontSizeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 16,
    )

    val fontStyle = settingsRepository.fontStyleFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FontStyleType.DEFAULT,
    )

    val simpleEdit = settingsRepository.simpleEditFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    init {
        when {
            copySource != -1L -> loadNote(copySource, isCopy = true)

            noteId != -1L -> loadNote(noteId, isCopy = false)

            // A new note created from a tag's list starts in that tag.
            addTag != 0L -> persistence.updateNote { it.copy(tagId = addTag) }
        }
        persistence.startAutosave(viewModelScope, editor.contentChanges)
        rememberStoredId()
    }

    // A new note (or copy) gets its id on its first save. Kept as the screen's argument, so a
    // screen restored after process death opens that note instead of a blank one.
    private fun rememberStoredId() {
        viewModelScope.launch {
            persistence.state.map { it.note.id }.distinctUntilChanged().collect { id ->
                if (id > 0L && id != noteId) {
                    savedStateHandle["idNote"] = id
                    savedStateHandle["copySource"] = -1L
                }
            }
        }
    }

    private fun loadNote(id: Long, isCopy: Boolean) {
        viewModelScope.launch {
            val found = persistence.load(id, isCopy, editor::load)
            // A note that could not be read stays read-only, so an empty editor is never
            // saved as (or over) it.
            _lockEditor.value = !found || isReadTrashOnly
        }
    }

    override fun onCleared() {
        // One last-chance save for lifecycle teardown. It does nothing once the editor was
        // closed (explicit exit, trash, delete, restore, discard), so it cannot duplicate an
        // insert or undo any of those.
        appScope.launch { persistence.save() }
        super.onCleared()
    }

    /** The user leaves the editor: no queued or later autosave may write the note again. */
    fun markExitHandled() = persistence.close()

    /**
     * Flush the current editor state as soon as the Activity leaves the foreground.
     * ProcessLifecycleOwner re-locks/closes SQLCipher later in the background transition,
     * so this closes the autosave debounce window before the vault is closed.
     */
    fun saveForBackground() {
        appScope.launch { persistence.save() }
    }

    /** Persists the note; returns once the database reports success or failure. */
    suspend fun saveNote(): Boolean = persistence.save()

    /** Puts the stored note back as it was when the editor opened it. */
    suspend fun discardChanges(): Boolean = persistence.discard()

    /** Restores the note from the trash. */
    suspend fun noteRestore(): Boolean = persistence.restoreFromTrash()

    /** Moves the note to the trash; returns once the write completed. */
    suspend fun noteMoveToTrash(): Boolean = persistence.moveToTrash()

    /** Inserts a copy of the note, titled by [NoteCopyTitle]; returns its id, or -1 on failure. */
    suspend fun noteDuplicate(): Long = persistence.duplicate()

    /** Whether the note is a new one with nothing in it yet: nothing to copy, save or discard. */
    val isBlankDraft: Boolean get() = persistence.isBlankDraft

    /** Whether discarding would undo anything (see [NotePersistence.hasChanges]). */
    suspend fun hasChanges(): Boolean = persistence.hasChanges()

    /** Deletes the note from the database for good. */
    suspend fun noteDelete(): Boolean = persistence.delete()

    fun updateTitle(newTitle: String) {
        persistence.updateNote { it.copy(title = newTitle) }
    }

    /**
     * Moves the note to tag [tagId]. The editor shows it at once; it is stored like a title
     * change, by the autosave or the save on leaving, together with the editor's content.
     */
    fun updateTagNote(tagId: Long) {
        // "No tag" (0) is stored as null, its only representation.
        persistence.updateNote { it.copy(tagId = tagId.takeIf { id -> id > 0L }) }
    }

    fun updateFontSize(size: Int) {
        // The app scope: closing the editor right after a change must not cancel the write.
        appScope.launch { settingsRepository.setFontSize(size) }
    }

    /** Locks or unlocks the editor. A note that could not be read, or one in the trash, stays locked. */
    fun toggleLockEditor() {
        if (contentLoadFailed.value || isReadTrashOnly) return
        _lockEditor.value = !_lockEditor.value
    }

    private fun canEdit(): Boolean = !isReadTrashOnly && !_lockEditor.value

    // --- blocks ----------------------------------------------------------------------------

    /** Position of [block] in the editor, by identity; -1 when it is no longer there. */
    fun indexOfBlock(block: Block): Int = editor.blocks.indexOfFirst { it.id == block.id }

    /** The field of [block] (for a list: one of its items) took focus. */
    fun onBlockFocused(block: Block) = editor.selection.onFocused(block.id)

    /** The field of [block] (for a list: all of its items) lost focus. */
    fun onBlockFocusLost(block: Block) = editor.selection.onFocusLost(block.id)

    /** The user works on [block] without its field taking focus (a list item, a block sheet). */
    fun onBlockInteraction(block: Block) = editor.selection.onInteraction(block.id)

    /**
     * Adds a block of [blockType] after the focused block (or the last one the user touched).
     * An empty first text block at that point is replaced instead.
     */
    fun addBlock(blockType: BlockType) {
        if (canEdit()) editor.addBlock(BlockFactory.createBlock(blockType))
    }

    /** Adds a block of [blockType] after position [afterIndex] and focuses it. */
    fun addBlockAfter(afterIndex: Int, blockType: BlockType) {
        if (canEdit()) editor.addBlockAfter(afterIndex, BlockFactory.createBlock(blockType))
    }

    /** Focuses a trailing text block (the tap area below the last block). */
    fun addBlockToEnd() {
        if (canEdit()) editor.addBlockToEnd()
    }

    /**
     * Removes [block] if [blockRemoveAction] allows it; see [BlockEditorState.removeBlock].
     * Does nothing in a locked or read-only editor.
     */
    fun removeBlock(block: Block, blockRemoveAction: BlockRemoveAction, isReFocus: Boolean = true) {
        if (canEdit()) editor.removeBlock(block, blockRemoveAction, isReFocus)
    }

    fun replaceBlock(targetBlockIndex: Int, newBlock: Block): Boolean =
        canEdit() && editor.replaceBlock(targetBlockIndex, newBlock)

    /**
     * Sets the text of a text-bearing [block] and records it for undo/redo. The previous
     * text is read from the block itself, so undo always restores what was really there.
     */
    fun onBlockTextChanged(block: Block, newText: String) {
        if (canEdit() && block is TextualBlock) editor.changeValue(block.id, block.text, newText, mergeable = true)
    }

    /** Sets the items of a list [block] (typing, checking, adding or removing items). */
    fun onListItemsChanged(block: Block.ListBlock, newItems: List<ItemListBlock>, mergeable: Boolean) {
        if (canEdit()) editor.changeValue(block.id, block.items, newItems, mergeable)
    }

    /** Sets the link of [block]; a link being edited ([editLink]) is committed with it. */
    fun onLinkChanged(block: Block.LinkBlock, newLink: LinkDataBlock) {
        if (!canEdit()) return
        _editingLinkIds.update { it - block.id }
        editor.changeValue(block.id, block.block, newLink, mergeable = false)
    }

    /**
     * Puts a saved link back into URL entry, keeping its address to edit. The saved link stays
     * in the block until the user commits a new one ([onLinkChanged]), so a save in the
     * meantime (autosave, Back, the app going to the background) still stores it.
     */
    fun editLink(block: Block.LinkBlock) {
        if (!canEdit()) return
        _editingLinkIds.update { it + block.id }
        editor.selection.onInteraction(block.id)
    }

    /**
     * Applies toolbar [tool] to the block the user works on (see applyTool); [exact] for a
     * specific heading level.
     */
    fun applyTool(tool: BlockType, exact: Boolean = false) {
        if (canEdit()) editor.applyTool(tool, exact)
    }

    /** The title's "Next": the first block takes focus. */
    fun focusFirstBlock() {
        if (canEdit()) editor.focusFirstBlock()
    }

    /** The toolbar's "Show keyboard": back into the block the user was writing in. */
    fun focusWorkingBlock() {
        if (canEdit()) editor.focusWorkingBlock()
    }

    fun undo() {
        if (canEdit()) editor.undo()
    }

    fun redo() {
        if (canEdit()) editor.redo()
    }

    // --- toolbar: acts on the block the user last worked on --------------------------------

    fun moveBlock(up: Boolean) {
        if (canEdit()) editor.moveInteracted(up)
    }

    fun canMoveBlock(up: Boolean): Boolean = canEdit() && editor.canMoveInteracted(up)

    fun canRemoveInteractedBlock(): Boolean = canEdit() && editor.canRemoveInteracted()

    fun removeInteractedBlock() {
        if (canEdit()) editor.removeInteracted()
    }

    /** Whether the block the user works on is the first, the last or in between. */
    fun getMoveBlockState(): Int = editor.interactedBlockPosition()
}
