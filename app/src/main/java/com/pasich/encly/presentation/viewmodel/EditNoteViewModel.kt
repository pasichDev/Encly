package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.AppLogger
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.R
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.datasource.local.FontStyleType
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.settings.FontSizeUseCase
import com.pasich.encly.domain.usecase.settings.FontStyleUseCase
import com.pasich.encly.domain.usecase.settings.SimpleEditUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockOperations
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.factory.BlockFactory
import com.pasich.encly.dynamicBlocks.focus.CentralizedFocusManager
import com.pasich.encly.dynamicBlocks.utils.BlockUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SaveStatusNote {
    OLD, SAVING, SAVED, LOADING,
}

@HiltViewModel
class EditNoteViewModel
@Inject constructor(
    private val notesRepository: NotesRepository,
    savedStateHandle: SavedStateHandle,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val updateNoteTagUseCase: UpdateNoteTagUseCase,
    private val fontSizeUseCase: FontSizeUseCase,
    private val fontStyleUseCase: FontStyleUseCase,
    private val simpleEditUseCase: SimpleEditUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(LoadNoteState())
    val state: StateFlow<LoadNoteState> get() = _state

    // Block editing mode - new state
    private val _isBlockEditMode = MutableStateFlow(false)
    val isBlockEditMode: StateFlow<Boolean> get() = _isBlockEditMode

    // List of blocks to be displayed and edited
    private val _blocks =
        SnapshotStateList<Block>().apply { add(Block.TextBlock(placeholder = R.string.press_to_edit)) }
    val blocks: List<Block> get() = _blocks

    private val _status = MutableStateFlow(SaveStatusNote.OLD)
    val status: StateFlow<SaveStatusNote> get() = _status

    private val _lockEditor = MutableStateFlow(false)
    val lockEditor: StateFlow<Boolean> get() = _lockEditor

    // Font size flow
    val fontSize: StateFlow<Int> = fontSizeUseCase.fontSizeFlow.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 16
    )

    // Font style flow
    val fontStyle = fontStyleUseCase.fontStyleFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FontStyleType.MODERN_SIMPLE
    )

    // Simple Edit flow
    val simpleEdit = simpleEditUseCase.simpleEditFlow.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false
    )

    private val noteId: Long = savedStateHandle["idNote"]
        ?: -1 // Note identifier; when creating a new note it will be -1
    internal val copySource: Long =
        savedStateHandle["copySource"] ?: -1 // Identifier of the note to copy, if specified
    val isReadTrashOnly: Boolean =
        savedStateHandle["isReadTrashOnly"] as? Boolean == true // Flag for reading a note only from the trash
    internal val addTag: Long = savedStateHandle["addTag"] ?: 0

    private val blockOperations = BlockOperations(_blocks)

    // Properties for managing undo/redo
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> get() = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> get() = _canRedo

    // Centralized focus manager
    private val _focusManager = CentralizedFocusManager()
    val focusManager: CentralizedFocusManager get() = _focusManager

    // Current focus (for backward compatibility)
    val currentFocusIndex: StateFlow<Int> = _focusManager.currentFocusIndex
    val lastInteractionIndex: StateFlow<Int> = _focusManager.lastInteractionIndex

    init {
        initLoad()
        observeBlocksForLiveSave()
    }

    private fun initLoad() {
        when {
            copySource != -1L -> loadNote(copySource, isCopy = true)
            noteId != -1L -> loadNote(noteId)
            addTag != 0L -> {
                _state.value = LoadNoteState(
                    note = _state.value.note.copy(tagId = addTag),
                )
            }

            else -> {
                // A comment can be left here if this is a normal case
            }
        }
    }

    private fun loadNote(
        noteId: Long,
        isCopy: Boolean = false,
    ) {
        viewModelScope.launch {
            _status.value = SaveStatusNote.LOADING
            try {
                val note = notesRepository.getNoteById(noteId)
                note?.let {
                    // Load the blocks from the note's content
                    loadBlocksFromNote(it)

                    delay(500)

                    // Update the note state depending on the mode
                    updateNoteState(it, isCopy)

                    _lockEditor.value = isReadTrashOnly
                }

                AppLogger.d("EditNoteViewModel", "Note loaded successfully with ID: $noteId")
            } catch (e: Exception) {
                AppLogger.e("EditNoteViewModel", "Error loading note: ${e.message}")
            }
            _status.value = SaveStatusNote.OLD
        }
    }

    // True when a note had stored content that could not be parsed/decrypted. Guards
    // saveNote so a subsequent edit does not overwrite the unreadable original.
    private var contentLoadFailed = false

    /**
     * Loads the note's blocks from its JSON content.
     */
    private fun loadBlocksFromNote(note: Note) {
        if (note.value.isNotEmpty()) {
            try {
                val loadedBlocks = BlockConverter.jsonToBlocks(note.value)
                if (loadedBlocks.isNotEmpty()) {
                    _blocks.clear()
                    _blocks.addAll(loadedBlocks)
                    updateUndoRedoState()
                } else {
                    // Non-empty stored content but nothing parsed back — treat as a
                    // load failure and protect the original from being overwritten.
                    contentLoadFailed = true
                    AppLogger.e("EditNoteViewModel", "Note content present but failed to parse")
                }
            } catch (e: Exception) {
                contentLoadFailed = true
                AppLogger.e("EditNoteViewModel", "Error converting blocks from JSON: ${e.message}")
            }
        }
    }

    /**
     * Updates the note state depending on the mode (copy or original).
     */
    private fun updateNoteState(
        note: Note,
        isCopy: Boolean,
    ) {
        if (isCopy) {
            // Copy: create a new note (id = -1) so it is saved as a fresh record.
            val copy = note.copy(
                id = -1,
                title = note.title + " (Copy)",
                date = System.currentTimeMillis(),
                tagId = note.tagId ?: -1L,
            )
            _state.value = LoadNoteState(note = copy, backupNote = copy)
        } else {
            // Existing note: keep its id.
            _state.value = LoadNoteState(note = note, backupNote = note)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeBlocksForLiveSave() {
        var isInitialized = false
        viewModelScope.launch {
            snapshotFlow { _blocks.toList() }.flatMapLatest { blocks ->
                    // Create flows for each block
                    combine(
                        blocks.mapNotNull { block ->
                            when (block) {
                                is Block.TextBlock -> block.text
                                is Block.HBlock -> block.text
                                is Block.QuoteBlock -> block.text
                                is Block.LinkBlock -> block.block.map { it.url }
                                is Block.ListBlock -> block.items.map { it.toString() }
                                else -> null
                            }
                        },
                    ) { it.toList() }
                }.debounce(2000).collect {
                    if (isInitialized) {
                        saveNote()
                    } else {
                        isInitialized = true
                    }
                }
        }
    }

    fun saveNote(actionButton: Boolean = false, saveBackupVersion: Boolean = false) {
        if (isReadTrashOnly) return
        // Never overwrite content that failed to load/decrypt.
        if (contentLoadFailed) {
            _status.value = SaveStatusNote.OLD
            return
        }
        _status.value = SaveStatusNote.SAVING

        val currentNote = if (saveBackupVersion) _state.value.backupNote else _state.value.note
        viewModelScope.launch {
            try {
                val blocksJson = if (!saveBackupVersion) BlockConverter.blocksToJson(blocks.filter {
                    when (it) {
                        is Block.TextBlock -> it.text.value.isNotBlank()
                        is Block.HBlock -> it.text.value.isNotBlank()
                        is Block.QuoteBlock -> it.text.value.isNotBlank()
                        is Block.ListBlock -> it.items.value.any { item -> item.value.isNotBlank() }
                        is Block.LinkBlock -> true
                        is Block.SeparatorBlock -> true
                    }
                }) else currentNote.value
                if (isNoteEmpty()) {
                    _status.value = SaveStatusNote.OLD
                    return@launch
                }

                // Choose the save strategy (update or create)
                if (currentNote.id != -1L) {
                    // Check whether the content or the title has changed
                    val timestamp = if (_state.value.backupNote.hasContentChanged(
                            blocksJson, _state.value.note.title
                        )
                    ) System.currentTimeMillis() else currentNote.date
                    withContext(Dispatchers.IO) {
                        notesRepository.updateNote(
                            currentNote.copy(
                                value = blocksJson,
                                date = timestamp,
                            ),
                        )
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        if (saveBackupVersion) return@withContext
                        val insertedId = notesRepository.insertNote(
                            Note.new(
                                title = currentNote.title,
                                value = blocksJson,
                                tagId = currentNote.tagId
                            ),
                        )
                        if (!actionButton) {
                            _state.value = _state.value.copy(
                                note = currentNote.copy(id = insertedId),
                            )
                        }
                    }

                }

                _status.value = SaveStatusNote.SAVED
                AppLogger.d(
                    "EditNoteViewModel",
                    "Note saved successfully with ID: ${_state.value.note.id}",
                )
            } catch (e: Exception) {
                AppLogger.e("EditNoteViewModel", "Error saving note: ${e.message}")
                _status.value = SaveStatusNote.OLD
            }
        }
    }

    /**
     * Checks whether the note is empty (has no content to save)
     */
    private fun isNoteEmpty(): Boolean =
        blocks.isEmpty() || (blocks.size == 1 && blocks[0] is Block.TextBlock && (blocks[0] as Block.TextBlock).text.value.isEmpty())


    /**
     * Sets focus on a block
     */
    fun setFocusedBlockIndex(
        index: Int,
        ignore: Boolean = false,
    ) {
        _focusManager.setFocus(index, ignore)
    }

    /**
     * Sets the last interaction index
     */
    fun setLastInteractionIndex(index: Int) {
        _focusManager.setLastInteraction(index)
    }

    /**
     * Updates the current focus index without calling requestFocus (to avoid recursion)
     */
    fun updateCurrentFocusIndex(index: Int) {
        _focusManager.updateCurrentFocusIndex(index)
    }

    fun addBlock(blockType: BlockType) {
        if (isReadTrashOnly) return

        // Determine the index for the new block - use the current focus or the last interaction
        val currentFocusIndex = _focusManager.currentFocusIndex.value
        val baseIndex =
            if (currentFocusIndex >= 0) currentFocusIndex else _focusManager.lastInteractionIndex.value
        var targetIndex = (baseIndex + 1).coerceAtMost(blocks.size)

        // Check the first block and remove it if it is an empty text block
        cleanEmptyFirstBlockIfNeeded()?.let { targetIndex = it }

        // Handle the special case of a text block
        if (blockType == BlockType.TEXT) {
            if (appendTextToLastBlockIfPossible()) {
                return
            }
        }

        // If this is not a special case, or it was not handled, create a new block
        val newBlock = createNewBlockByType(blockType) ?: return

        // Add the block and update the state
        blockOperations.addBlock(targetIndex, newBlock)
        updateUndoRedoState()

        // Set focus on the new block with a slight delay, only if it is not a ListBlock
        setLastInteractionIndex(targetIndex)

        // For a ListBlock, do not set focus automatically
        if (blockType != BlockType.LIST_CHECK && blockType != BlockType.LIST_NUMBER) {
            viewModelScope.launch {
                delay(50) // Slight delay to let the UI updates finish
                setFocusedBlockIndex(targetIndex)
            }
        }
    }

    /**
     * Adds a new block after the specified index
     */
    fun addBlockAfter(
        afterIndex: Int,
        blockType: BlockType,
    ) {
        AppLogger.d(
            "EditNoteViewModel",
            "addBlockAfter called: afterIndex=$afterIndex, blockType=$blockType, isReadTrashOnly=$isReadTrashOnly",
        )

        if (isReadTrashOnly) return

        val targetIndex = (afterIndex + 1).coerceAtMost(blocks.size)

        // Create a new block
        val newBlock = createNewBlockByType(blockType) ?: return
        AppLogger.d(
            "EditNoteViewModel",
            "Created new block: ${newBlock::class.simpleName}, targetIndex=$targetIndex"
        )

        // Add the block and update the state
        blockOperations.addBlock(targetIndex, newBlock)
        updateUndoRedoState()

        // Always set focus on the new block (even for a ListBlock)
        // This is important for correct scrolling
        setLastInteractionIndex(targetIndex)

        // Use the retrying method for better reliability
        _focusManager.setFocusWithRetry(targetIndex, maxRetries = 5, delayMs = 50L)
        AppLogger.d("EditNoteViewModel", "Focus set to new block with retry: $targetIndex")
    }

    /**
     * Clears the first block if it is an empty text block
     * @return The index for the new block (0 if the first one was removed) or null
     */
    private fun cleanEmptyFirstBlockIfNeeded(): Int? {
        val first = blocks.firstOrNull() ?: return null

        if (first is Block.TextBlock && first.text.value.isEmpty()) {
            blockOperations.removeBlock(0)
            return 0
        }
        return null
    }

    /**
     * Tries to append a line break to the last text block
     * @return true if the text was appended to the last block
     */
    private fun appendTextToLastBlockIfPossible(): Boolean {
        val lastBlock = _blocks.lastOrNull()
        if (lastBlock is Block.TextBlock) {
            lastBlock.text.value += "\n"
            return true
        }
        return false
    }

    /**
     * Creates a new block of the appropriate type using the factory
     */
    private fun createNewBlockByType(blockType: BlockType): Block? =
        BlockFactory.createBlock(blockType)

    /**
     * Removes a block from the note using different strategies depending on the removal action type
     */
    fun removeBlock(
        block: Block,
        blockRemoveAction: BlockRemoveAction,
        isReFocus: Boolean = true,
    ) {
        AppLogger.d(
            "EditNoteViewModel",
            "removeBlock called: action=$blockRemoveAction, blockType=${block::class.simpleName}"
        )

        val index = _blocks.indexOf(block)
        if (index == -1) {
            AppLogger.d("EditNoteViewModel", "Block not found in list")
            return
        }

        // Check whether the block can be removed (at least one block must remain)
        if (blocks.size <= 1 && blockRemoveAction != BlockRemoveAction.REMOVE_BACKSPACE) {
            AppLogger.d("EditNoteViewModel", "Cannot remove: size check failed")
            return
        }

        // Choose the removal strategy depending on the action
        when (blockRemoveAction) {
            BlockRemoveAction.REMOVE -> {
                AppLogger.d("EditNoteViewModel", "Performing REMOVE")
                performBlockRemoval(index, isReFocus)
            }

            BlockRemoveAction.REMOVE_BACKSPACE -> {
                AppLogger.d("EditNoteViewModel", "Checking REMOVE_BACKSPACE")
                if (canRemoveTextBlock(block)) {
                    AppLogger.d("EditNoteViewModel", "Performing REMOVE_BACKSPACE")
                    performBlockRemoval(index, isReFocus)
                } else {
                    AppLogger.d("EditNoteViewModel", "Cannot remove block with REMOVE_BACKSPACE")
                }
            }

            BlockRemoveAction.REMOVE_BACKSPACE_LIST -> {
                AppLogger.d("EditNoteViewModel", "Performing REMOVE_BACKSPACE_LIST")
                performBlockRemoval(index, isReFocus)
            }
        }
    }

    /**
     * Checks whether a text block can be removed (it must be empty)
     */
    private fun canRemoveTextBlock(block: Block): Boolean {
        AppLogger.d(
            "EditNoteViewModel",
            "canRemoveTextBlock: blocks.size=${blocks.size}, isEmpty=${
                BlockUtils.isBlockEmpty(
                    block,
                )
            }, blockType=${block::class.simpleName}",
        )

        if (blocks.size <= 1) {
            AppLogger.d("EditNoteViewModel", "Cannot remove: only one block left")
            return false
        }

        val isEmpty = BlockUtils.isBlockEmpty(block)
        AppLogger.d("EditNoteViewModel", "Block empty check result: $isEmpty")
        return isEmpty
    }

    /**
     * Performs the block removal and updates the focus
     */
    private fun performBlockRemoval(
        index: Int,
        isReFocus: Boolean,
    ) {

        blockOperations.removeBlock(index)
        updateUndoRedoState()
        // Update the focus if required
        if (isReFocus) {
            val newFocusIndex = _focusManager.findPreviousFocusableBlock(index, _blocks)
            _focusManager.setFocus(newFocusIndex, moveCursorToEnd = true)
            _focusManager.setLastInteraction(newFocusIndex)
        }
    }

    fun replaceBlock(
        targetBlockIndex: Int,
        newBlock: Block,
    ): Boolean {
        AppLogger.d(
            "EditNoteViewModel",
            "replaceBlock called: targetBlockIndex=$targetBlockIndex, oldBlock=${
                if (targetBlockIndex in _blocks.indices) {
                    _blocks[targetBlockIndex]::class.simpleName
                } else {
                    "null"
                }
            }, newBlock=${newBlock::class.simpleName}",
        )

        return if (targetBlockIndex in _blocks.indices) {
            blockOperations.replaceBlock(targetBlockIndex, newBlock)
            updateUndoRedoState()

            // Always set focus on the replaced block
            setLastInteractionIndex(targetBlockIndex)

            // Use the retrying method for better reliability
            _focusManager.setFocusWithRetry(targetBlockIndex, maxRetries = 5, delayMs = 50L)
            _focusManager.setLastInteraction(targetBlockIndex)
            updateCurrentFocusIndex(targetBlockIndex)
            AppLogger.d("EditNoteViewModel", "Focus set to replaced block with retry: $targetBlockIndex")

            AppLogger.d("EditNoteViewModel", "Block replaced successfully")
            true
        } else {
            AppLogger.e(
                "EditNoteViewModel",
                "Invalid index: $targetBlockIndex. Must be between 0 and ${_blocks.size - 1}.",
            )
            false
        }
    }

    /**
     * Registers a text change in a block to support undo/redo
     */
    fun registerTextChange(
        index: Int,
        oldText: String,
        newText: String,
    ) {
        blockOperations.registerTextChange(index, oldText, newText)
        updateUndoRedoState()
    }

    /**
     * Registers a change in a block to support undo/redo
     */
    fun registerContentChange(
        index: Int,
        oldBlock: Block,
        newBlock: Block,
    ) {
        blockOperations.registerContentChange(index, oldBlock, newBlock)
        updateUndoRedoState()
    }

    /**
     * Undoes the last action
     */
    fun undo() {
        if (blockOperations.undo()) {
            updateUndoRedoState()
        }
    }

    /**
     * Redoes the undone action
     */
    fun redo() {
        if (blockOperations.redo()) {
            updateUndoRedoState()
        }
    }

    /**
     * Updates the undo/redo availability state
     */
    private fun updateUndoRedoState() {
        _canUndo.value = blockOperations.canUndo()
        _canRedo.value = blockOperations.canRedo()
    }

    /**
     * Updates the note title in the state
     */
    fun updateTitle(newTitle: String) {
        _state.value = _state.value.copy(note = state.value.note.copy(title = newTitle))
    }

    // Method for toggling the block editing mode
    fun toggleBlockEditMode() {
        _isBlockEditMode.value = !_isBlockEditMode.value
    }

    fun toggleLockEditor() {
        _lockEditor.value = !_lockEditor.value
    }

    /**
     * Restores the note from the trash
     */
    fun noteRestore() {
        val currentNote = _state.value.note
        val currentNoteId = currentNote.id

        if (currentNoteId == -1L) {
            AppLogger.e("EditNoteViewModel", "Cannot restore note: current note ID is -1")
            return
        }

        // Get the current content of the note
        val blocksJson = BlockConverter.blocksToJson(blocks)

        viewModelScope.launch {
            // Update the note status through the use case
            val result = updateNoteTrashStatusUseCase.invoke(
                currentNote.copy(
                    value = blocksJson,
                ),
                false, // false means "not in the trash", i.e. restore
            )

            if (result) {
                AppLogger.d("EditNoteViewModel", "Note restored with ID: $currentNoteId")
            } else {
                AppLogger.e("EditNoteViewModel", "Failed to restore note with ID: $currentNoteId")
            }
        }
    }

    /**
     * Moves the current note to the trash (soft delete). Returns true on success.
     * Suspends until the write completes so the caller can safely navigate away after.
     */
    suspend fun noteMoveToTrash(): Boolean {
        val currentNote = _state.value.note
        if (currentNote.id == -1L) return false
        val blocksJson = BlockConverter.blocksToJson(blocks)
        return updateNoteTrashStatusUseCase.invoke(currentNote.copy(value = blocksJson), true)
    }

    /**
     * Inserts a copy of the current note (title + " (Copy)") as a new record.
     * Returns the new note id, or -1 on failure.
     */
    suspend fun noteDuplicate(): Long {
        val currentNote = _state.value.note
        val blocksJson = BlockConverter.blocksToJson(blocks)
        return try {
            notesRepository.insertNote(
                Note.new(
                    title = currentNote.title + " (Copy)",
                    value = blocksJson,
                    tagId = currentNote.tagId
                )
            )
        } catch (e: Exception) {
            AppLogger.e("EditNoteViewModel", "Error duplicating note: ${e.message}")
            -1L
        }
    }

    /**
     * Deletes the note completely from the database
     */
    fun noteDelete() {
        val currentNoteId = _state.value.note.id

        if (currentNoteId == -1L) {
            AppLogger.e("EditNoteViewModel", "Cannot delete note: current note ID is -1")
            return
        }

        viewModelScope.launch {
            try {
                // Delete the note from the database
                notesRepository.deleteNoteById(currentNoteId)
                AppLogger.d("EditNoteViewModel", "Note deleted with ID: $currentNoteId")
            } catch (e: Exception) {
                AppLogger.e("EditNoteViewModel", "Error deleting note: ${e.message}")
            }
        }
    }

    fun updateTagNote(tagId: Long) {
        val currentNote = _state.value.note

        // Update the UI state immediately for interface responsiveness
        _state.value = _state.value.copy(
            note = currentNote.copy(tagId = tagId),
        )

        // Apply the change in the database if the note is already saved
        if (currentNote.id != -1L) {
            viewModelScope.launch {
                updateNoteTagUseCase.invoke(currentNote, tagId)
            }
        }
        // If the note is new, the tag will be saved together with the note on the next save
    }

    fun updateFontSize(size: Int) {
        fontSizeUseCase.setFontSize(size, viewModelScope)
    }

    fun updateFontStyle(style: FontStyleType) {
        fontStyleUseCase.setFontStyle(style, viewModelScope)
    }

    fun moveBlock(up: Boolean) { // up = true - move up, false - move down
        val fromIndex = _focusManager.lastInteractionIndex.value
        val toIndex = if (up) fromIndex - 1 else fromIndex + 1

        if (fromIndex !in _blocks.indices || toIndex !in _blocks.indices) return

        blockOperations.moveBlock(fromIndex, toIndex)
        updateUndoRedoState()

        // Update the focus after moving the block
        _focusManager.setFocus(toIndex)
        _focusManager.setLastInteraction(toIndex)
    }

    fun getMoveBlockState(): Int {
        val index = _focusManager.lastInteractionIndex.value

        if (index == 0) {
            print("First block cannot be moved up")
            return 1
        } else if (index == _blocks.size - 1) {
            print("Last block cannot be moved down")
            return 2
        }
        return -1
    }

    fun addBlockToEnd() {
        if (isReadTrashOnly) return

        // Check if the last block is an empty TextBlock
        val lastBlock = _blocks.lastOrNull()
        if (lastBlock is Block.TextBlock) {
            setLastInteractionIndex(_blocks.size - 1)
            viewModelScope.launch {
                delay(50)
                setFocusedBlockIndex(_blocks.size - 1)
            }
            return
        }

        // Create a new block
        val newBlock = createNewBlockByType(BlockType.TEXT) ?: return

        // Add the block to the end of the list
        blockOperations.addBlock(_blocks.size, newBlock)
        updateUndoRedoState()

        // Set focus to the new block with a slight delay
        setLastInteractionIndex(_blocks.size - 1)
        viewModelScope.launch {
            delay(50)
            setFocusedBlockIndex(_blocks.size - 1)
        }
    }


    override fun onCleared() {
        super.onCleared()
        saveNote()
        _focusManager.cleanup()
    }

}

data class LoadNoteState(
    val note: Note = Note(id = -1),
    val backupNote: Note = Note(id = -1),
)

