package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.pasich.encly.core.di.ApplicationScope
import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.NoteTextOverride
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockFactory
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.model.TextualBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.editor.state.FocusRequest
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.state.SlashPicker
import com.pasich.encly.editor.state.addBlockAfter
import com.pasich.encly.editor.state.addBlockToEnd
import com.pasich.encly.editor.state.applyShortcut
import com.pasich.encly.editor.state.applySlash
import com.pasich.encly.editor.state.applyTemplate
import com.pasich.encly.editor.state.applyTool
import com.pasich.encly.editor.state.backspaceAtStart
import com.pasich.encly.editor.state.breakLine
import com.pasich.encly.editor.state.canMoveInteracted
import com.pasich.encly.editor.state.canRemoveInteracted
import com.pasich.encly.editor.state.duplicateBlockOf
import com.pasich.encly.editor.state.duplicateInteracted
import com.pasich.encly.editor.state.focusFirstBlock
import com.pasich.encly.editor.state.focusWorkingBlock
import com.pasich.encly.editor.state.indexOf
import com.pasich.encly.editor.state.interactedBlockPosition
import com.pasich.encly.editor.state.isBlankNote
import com.pasich.encly.editor.state.listBackspaceAtStart
import com.pasich.encly.editor.state.listLineBreak
import com.pasich.encly.editor.state.listTypingStep
import com.pasich.encly.editor.state.moveBlockOf
import com.pasich.encly.editor.state.moveInteracted
import com.pasich.encly.editor.state.pasteLink
import com.pasich.encly.editor.state.removeInteracted
import com.pasich.encly.editor.state.toggleChecklistLock
import com.pasich.encly.presentation.editor.persistence.LoadNoteState
import com.pasich.encly.presentation.editor.persistence.NoteCopyTitle
import com.pasich.encly.presentation.editor.persistence.NotePersistence
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The note editor. Wires the block content ([BlockEditorState]) to the stored note
 * ([NotePersistence]) and decides whether the note may be edited at all (locked, or read-only
 * from the trash). Focus is plain state here; the UI owns the FocusRequesters.
 */
@Suppress("LongParameterList") // Injected dependencies; grouping them would only obscure wiring.
@AssistedInject
class EditNoteViewModel(
    notesRepository: NotesRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
    updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    updateNoteArchiveStatusUseCase: UpdateNoteArchiveStatusUseCase,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    copyTitle: NoteCopyTitle,
    lockEvents: VaultLockEvents = NeverLocked,
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
        updateNoteArchiveStatusUseCase = updateNoteArchiveStatusUseCase,
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

    /** The block whose field has focus; null while none has. */
    val focusedBlockId: StateFlow<String?> get() = editor.selection.focusedBlockId

    /** Which block the editor UI should focus next. */
    val focusRequests: Flow<FocusRequest> get() = editor.selection.requests

    /** Carries out a focus request at once where its field exists; set by the editor screen while composed. */
    var immediateFocus: ((FocusRequest) -> Unit)?
        get() = editor.selection.immediateFocus
        set(value) {
            editor.selection.immediateFocus = value
        }

    /** The slash picker (UI state; the paragraph it belongs to is named in its menu). */
    val slashPicker: SlashPicker get() = editor.slash

    /** The find bar: open or not, its query and matches. Never saved; cleared when the vault locks. */
    val find: StateFlow<FindState> get() = editor.find.state

    private val _lockEditor = MutableStateFlow(false)
    val lockEditor: StateFlow<Boolean> get() = _lockEditor

    // Link blocks whose saved address is open in URL entry. Editor UI state only: the block
    // keeps its stored link until a new one is committed.
    private val _editingLinkIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the link blocks the user is editing ([editLink]); they show URL entry. */
    val editingLinkIds: StateFlow<Set<String>> get() = _editingLinkIds

    /**
     * What this note sets for its own text size and line spacing; the rest follows the defaults.
     * They are columns of the note in the vault, stored with it like the title.
     */
    val textOverride: StateFlow<NoteTextOverride> = persistence.state.map { state ->
        NoteTextOverride(
            size = state.note.textSize,
            spacing = state.note.lineSpacing?.let { enumValues<NoteLineSpacing>().getOrNull(it) },
        )
    }.distinctUntilChanged().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        NoteTextOverride(),
    )

    /** The text size in effect: the note's own, else the default from Settings → Appearance. */
    val fontSize: StateFlow<Int> = combine(settingsRepository.fontSizeFlow, textOverride) { default, own ->
        own.size ?: default
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 16,
    )

    val fontStyle = settingsRepository.fontStyleFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FontStyleType.DEFAULT,
    )

    val noteLineSpacing = combine(settingsRepository.noteLineSpacingFlow, textOverride) { default, own ->
        own.spacing ?: default
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NoteLineSpacing.DEFAULT,
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
        keepFindInStep()
        rememberStoredId()
        // Session lock hook (security): a re-lock drops the note's plaintext at once, even
        // while the app is in the background and this screen is not composed.
        clearOnLock(lockEvents) {
            persistence.close()
            // Also closes the find bar and the slash picker: no query or match survives a lock.
            editor.load(emptyList())
            persistence.updateNote { it.copy(title = "") }
        }
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

    /** While the find bar is open, its matches follow the text. Read-only: the blocks are only looked at. */
    @OptIn(FlowPreview::class)
    private fun keepFindInStep() {
        viewModelScope.launch {
            editor.find.state.map { it.isOpen }.distinctUntilChanged().collectLatest { open ->
                if (open) editor.contentChanges.debounce(FIND_REFRESH_MS).collect { editor.find.refresh(editor.blocks) }
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

    /** Moves the note to the archive, or back to the list; returns once the write completed. */
    suspend fun noteSetArchived(archived: Boolean): Boolean = persistence.setArchived(archived)

    /** Marks the note with [color]. Stored with the next save, like the title. */
    fun updateColor(color: NoteColor) {
        if (!isReadTrashOnly) persistence.updateNote { it.copy(color = color.key) }
    }

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

    /** Sets this note's own text size; the default in Settings stays as it is. */
    fun updateFontSize(size: Int) {
        if (!isReadTrashOnly) persistence.updateNote { it.copy(textSize = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)) }
    }

    /** Sets this note's own line spacing; the default in Settings stays as it is. */
    fun updateNoteLineSpacing(value: NoteLineSpacing) {
        if (!isReadTrashOnly) persistence.updateNote { it.copy(lineSpacing = value.ordinal) }
    }

    /** "Use default": the note follows the text size and line spacing of Settings again. */
    fun useDefaultText() {
        if (!isReadTrashOnly) persistence.updateNote { it.copy(textSize = null, lineSpacing = null) }
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
    fun onBlockFocusLost(block: Block) {
        editor.selection.onFocusLost(block.id)
        // Leaving the paragraph leaves its "/" as plain text.
        if (editor.slash.menu.value?.blockId == block.id) editor.slash.dismiss()
    }

    /** The user works on [block] without its field taking focus (a list item, a block sheet). */
    fun onBlockInteraction(block: Block) {
        editor.selection.onInteraction(block.id)
        editor.slash.dismiss()
    }

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
        if (canEdit(block) && block is TextualBlock) editor.changeValue(block.id, block.text, newText, mergeable = true)
    }

    /** Sets the items of a list [block] (typing, checking, adding or removing items). */
    fun onListItemsChanged(block: Block.ListBlock, newItems: List<ItemListBlock>, mergeable: Boolean) {
        if (!canEdit(block)) return
        val step = if (mergeable) listTypingStep(block.items.value, newItems) else null
        editor.changeValue(block.id, block.items, newItems, mergeable, step)
    }

    // A block that left the note (converted, merged, undone) may still report a late edit from
    // its field; it must not reach the history.
    private fun canEdit(block: Block): Boolean = canEdit() && editor.indexOf(block.id) >= 0

    /**
     * A line break typed or pasted into the field of [block] (see breakLine). Returns the text the
     * field keeps, or null to keep the break as text: in simple editing, or a locked editor.
     */
    fun onLineBreak(block: Block, lineBreak: LineBreak): String? {
        if (!canEdit(block) || simpleEdit.value) return null
        // Enter with the slash picker showing picks the highlighted command instead of splitting.
        val picked = editor.slash.menu.value
            ?.takeIf { it.blockId == block.id && lineBreak.isEnter && it.isShown }
            ?.highlightedCommand
        val pickedHere = picked != null && editor.applySlash(block.id, picked)
        return if (pickedHere) "" else editor.breakLine(block.id, lineBreak)
    }

    /** Backspace at the start of [block]'s field; false when it does nothing there. */
    fun onBackspaceAtStart(block: Block): Boolean = canEdit(block) && editor.backspaceAtStart(block.id)

    /** A mark typed at the start of paragraph [block] ("# ", "- ", ...): it becomes that block. */
    fun onShortcut(block: Block, typed: String, shortcut: Shortcut): Boolean =
        canEdit(block) && !simpleEdit.value && editor.applyShortcut(block.id, typed, shortcut)

    /**
     * Paragraph [block] now reads [text]. [started]: a "/" was just typed into the empty
     * paragraph, which opens the slash picker (never in Simple Edit or a read-only editor);
     * otherwise an open picker follows the text.
     */
    fun onSlashTyped(block: Block, text: String, started: Boolean) {
        if (started && block is Block.TextBlock && canOfferSlash(block)) editor.slash.open(block.id)
        editor.slash.onText(block.id, text)
    }

    private fun canOfferSlash(block: Block): Boolean = canEdit(block) && !simpleEdit.value

    /** The slash picker's [command] chosen for paragraph [block]. */
    fun applySlash(block: Block, command: SlashCommand) {
        if (canEdit(block) && !simpleEdit.value) editor.applySlash(block.id, command)
    }

    /** A lone web address pasted into the empty paragraph [block]: it becomes a link card. */
    fun onLinkPasted(block: Block, pasted: String, link: LinkDataBlock): Boolean =
        canEdit(block) && !simpleEdit.value && editor.pasteLink(block.id, pasted, link)

    /** A line break in item [itemId] of list [block] (see listLineBreak). */
    fun onListLineBreak(block: Block.ListBlock, itemId: String, lineBreak: LineBreak): String? =
        if (canEdit(block)) editor.listLineBreak(block.id, itemId, lineBreak) else null

    /** Backspace at the start of item [itemId] of list [block]. */
    fun onListBackspaceAtStart(block: Block.ListBlock, itemId: String): Boolean =
        canEdit(block) && editor.listBackspaceAtStart(block.id, itemId)

    /** The cursor in [block]'s field moved to [offset]. */
    fun onCaretMoved(block: Block, offset: Int) = editor.selection.onCaret(block.id, offset)

    /** The cursor in item [itemId] of list [block] moved to [offset]. */
    fun onListItemCaret(block: Block, itemId: String, offset: Int) =
        editor.selection.onItemCaret(block.id, itemId, offset)

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

    /** Moves [block] one place [up] (or down): TalkBack's actions on a block. */
    fun moveBlockOf(block: Block, up: Boolean) {
        if (canEdit(block)) editor.moveBlockOf(block.id, up)
    }

    fun canRemoveInteractedBlock(): Boolean = canEdit() && editor.canRemoveInteracted()

    fun removeInteractedBlock() {
        if (canEdit()) editor.removeInteracted()
    }

    /** Locks or unlocks the checklist [block] (see toggleChecklistLock). */
    fun toggleChecklistLock(block: Block) {
        if (canEdit(block)) editor.toggleChecklistLock(block.id)
    }

    /** Duplicates the block the user works on, right after it. */
    fun duplicateInteractedBlock() {
        if (canEdit()) editor.duplicateInteracted()
    }

    /** Duplicates [block], right after it: TalkBack's and the block sheets' action. */
    fun duplicateBlock(block: Block) {
        if (canEdit(block)) editor.duplicateBlockOf(block.id)
    }

    // --- find in this note: read-only, so it works in a locked or trashed note too ---------------

    fun openFind() = editor.find.open()

    fun closeFind() = editor.find.close()

    fun onFindQueryChanged(query: String) = editor.find.search(query, editor.blocks)

    fun findNext() = editor.find.next()

    fun findPrevious() = editor.find.previous()

    // --- templates -------------------------------------------------------------------------

    /**
     * Whether to offer "Start from template": a brand-new note that is still completely empty,
     * and not in Simple Edit (it never shows block tools). Following the text, so it goes away
     * with the first character and comes back if undo makes the note blank again, but not once
     * the note has been saved (its stored id is set): from then on it is an existing note.
     */
    val templatesOffered: StateFlow<Boolean> = combine(
        persistence.state.map { it.note.title.isEmpty() && it.note.id <= 0L }.distinctUntilChanged(),
        editor.contentChanges.map { editor.isBlankNote() }.onStart { emit(editor.isBlankNote()) },
        simpleEdit,
        _lockEditor,
    ) { blankUnsaved, blankBody, simple, locked -> isNewNote && blankUnsaved && blankBody && !simple && !locked }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Starts the blank note from [blocks] (a template's, see NoteTemplate.blocks). */
    fun applyTemplate(blocks: List<Block>) {
        if (canEdit() && !simpleEdit.value) editor.applyTemplate(blocks)
    }

    /** Whether the block the user works on is the first, the last or in between. */
    fun getMoveBlockState(): Int = editor.interactedBlockPosition()
}

/** Creates [EditNoteViewModel] with the SavedStateHandle of its entry, navigation arguments included. */
@AssistedFactory
@ViewModelAssistedFactoryKey(EditNoteViewModel::class)
@ContributesIntoMap(AppScope::class)
fun interface EditNoteViewModelFactory : ViewModelAssistedFactory {
    override fun create(extras: CreationExtras): EditNoteViewModel = create(extras.createSavedStateHandle())

    fun create(@Assisted savedStateHandle: SavedStateHandle): EditNoteViewModel
}

/** The find bar refreshes its matches this long after the text stops changing. */
private const val FIND_REFRESH_MS = 100L

private const val MIN_TEXT_SIZE = 10
private const val MAX_TEXT_SIZE = 32

/** How long state shared with the UI is kept after the last collector leaves. */
private const val STOP_TIMEOUT_MS = 5_000L
