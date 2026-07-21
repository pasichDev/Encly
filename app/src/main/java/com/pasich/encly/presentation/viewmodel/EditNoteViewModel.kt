package com.pasich.encly.presentation.viewmodel

import android.util.Log
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

    // Режим редактирования блоков - новое состояние
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
        ?: -1 // Идентификатор заметки, если создать новую заметку, то будет -1
    internal val copySource: Long =
        savedStateHandle["copySource"] ?: -1 // Идентификатор заметки для копирования, если указан
    val isReadTrashOnly: Boolean =
        savedStateHandle["isReadTrashOnly"] as? Boolean == true // Флаг для чтения заметки только из корзины
    internal val addTag: Long = savedStateHandle["addTag"] ?: 0

    private val blockOperations = BlockOperations(_blocks)

    // Свойства для управления отменой/повтором
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> get() = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> get() = _canRedo

    // Централізований менеджер фокуса
    private val _focusManager = CentralizedFocusManager()
    val focusManager: CentralizedFocusManager get() = _focusManager

    // Поточний фокус (для зворотної сумісності)
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
                // Можна залишити коментар, якщо це нормальний випадок
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
                    // Загружаем блоки из контента заметки
                    loadBlocksFromNote(it)

                    delay(500)

                    // Обновляем состояние заметки в зависимости от режима
                    updateNoteState(it, isCopy)

                    _lockEditor.value = isReadTrashOnly
                }

                Log.d("EditNoteViewModel", "Note loaded successfully with ID: $noteId")
            } catch (e: Exception) {
                Log.e("EditNoteViewModel", "Error loading note: ${e.message}")
            }
            _status.value = SaveStatusNote.OLD
        }
    }

    /**
     * Загружает блоки из JSON в заметке
     */
    private fun loadBlocksFromNote(note: Note) {
        if (note.value.isNotEmpty()) {
            try {
                val loadedBlocks = BlockConverter.jsonToBlocks(note.value)
                if (loadedBlocks.isNotEmpty()) {
                    _blocks.clear()
                    _blocks.addAll(loadedBlocks)
                    updateUndoRedoState()
                }
            } catch (e: Exception) {
                Log.e(
                    "EditNoteViewModel",
                    "Error converting blocks from JSON: ${e.message}",
                )
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
                    // Створюємо потоки для кожного блоку
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

                // Выбираем стратегию сохранения (обновление или создание)
                if (currentNote.id != -1L) {
                    // Проверяем, есть ли изменения в контенте или заголовке
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
                Log.d(
                    "EditNoteViewModel",
                    "Note saved successfully with ID: ${_state.value.note.id}",
                )
            } catch (e: Exception) {
                Log.e("EditNoteViewModel", "Error saving note: ${e.message}")
                _status.value = SaveStatusNote.OLD
            }
        }
    }

    /**
     * Проверяет, пуста ли заметка (не имеет контента для сохранения)
     */
    private fun isNoteEmpty(): Boolean =
        blocks.isEmpty() || (blocks.size == 1 && blocks[0] is Block.TextBlock && (blocks[0] as Block.TextBlock).text.value.isEmpty())


    /**
     * Встановлює фокус на блок
     */
    fun setFocusedBlockIndex(
        index: Int,
        ignore: Boolean = false,
    ) {
        _focusManager.setFocus(index, ignore)
    }

    /**
     * Встановлює останній індекс взаємодії
     */
    fun setLastInteractionIndex(index: Int) {
        _focusManager.setLastInteraction(index)
    }

    /**
     * Обновляет текущий индекс фокуса без вызова requestFocus (для избежания рекурсии)
     */
    fun updateCurrentFocusIndex(index: Int) {
        _focusManager.updateCurrentFocusIndex(index)
    }

    fun addBlock(blockType: BlockType) {
        if (isReadTrashOnly) return

        // Определяем индекс для нового блока - используем текущий фокус или последнее взаимодействие
        val currentFocusIndex = _focusManager.currentFocusIndex.value
        val baseIndex =
            if (currentFocusIndex >= 0) currentFocusIndex else _focusManager.lastInteractionIndex.value
        var targetIndex = (baseIndex + 1).coerceAtMost(blocks.size)

        // Проверяем первый блок и удаляем его, если он пустой текстовый блок
        cleanEmptyFirstBlockIfNeeded()?.let { targetIndex = it }

        // Обрабатываем специальный случай с текстовым блоком
        if (blockType == BlockType.TEXT) {
            if (appendTextToLastBlockIfPossible()) {
                return
            }
        }

        // Если это не специальный случай или он не был обработан, создаем новый блок
        val newBlock = createNewBlockByType(blockType) ?: return

        // Добавляем блок и обновляем состояние
        blockOperations.addBlock(targetIndex, newBlock)
        updateUndoRedoState()

        // Устанавливаем фокус на новый блок с небольшой задержкой только если это не ListBlock
        setLastInteractionIndex(targetIndex)

        // Для ListBlock не устанавливаем автоматический фокус
        if (blockType != BlockType.LIST_CHECK && blockType != BlockType.LIST_NUMBER) {
            viewModelScope.launch {
                delay(50) // Небольшая задержка для завершения UI обновлений
                setFocusedBlockIndex(targetIndex)
            }
        }
    }

    /**
     * Добавляет новый блок после указанного индекса
     */
    fun addBlockAfter(
        afterIndex: Int,
        blockType: BlockType,
    ) {
        Log.d(
            "EditNoteViewModel",
            "addBlockAfter called: afterIndex=$afterIndex, blockType=$blockType, isReadTrashOnly=$isReadTrashOnly",
        )

        if (isReadTrashOnly) return

        val targetIndex = (afterIndex + 1).coerceAtMost(blocks.size)

        // Создаем новый блок
        val newBlock = createNewBlockByType(blockType) ?: return
        Log.d(
            "EditNoteViewModel",
            "Created new block: ${newBlock::class.simpleName}, targetIndex=$targetIndex"
        )

        // Добавляем блок и обновляем состояние
        blockOperations.addBlock(targetIndex, newBlock)
        updateUndoRedoState()

        // Встановлюємо фокус на новий блок завжди (навіть для ListBlock)
        // Це важливо для правильної прокрутки
        setLastInteractionIndex(targetIndex)

        // Використовуємо метод з повторними спробами для кращої надійності
        _focusManager.setFocusWithRetry(targetIndex, maxRetries = 5, delayMs = 50L)
        Log.d("EditNoteViewModel", "Focus set to new block with retry: $targetIndex")
    }

    /**
     * Очищает первый блок, если он пустой текстовый блок
     * @return Индекс для нового блока (0, если первый был удален) или null
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
     * Пытается добавить перенос строки к последнему текстовому блоку
     * @return true если текст был добавлен к последнему блоку
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
     * Создает новый блок соответствующего типа используя фабрику
     */
    private fun createNewBlockByType(blockType: BlockType): Block? =
        BlockFactory.createBlock(blockType)

    /**
     * Удаляет блок из заметки с разными стратегиями в зависимости от типа действия удаления
     */
    fun removeBlock(
        block: Block,
        blockRemoveAction: BlockRemoveAction,
        isReFocus: Boolean = true,
    ) {
        Log.d(
            "EditNoteViewModel",
            "removeBlock called: action=$blockRemoveAction, blockType=${block::class.simpleName}"
        )

        val index = _blocks.indexOf(block)
        if (index == -1) {
            Log.d("EditNoteViewModel", "Block not found in list")
            return
        }

        // Проверяем, можно ли удалить блок (должен остаться хотя бы один блок)
        if (blocks.size <= 1 && blockRemoveAction != BlockRemoveAction.REMOVE_BACKSPACE) {
            Log.d("EditNoteViewModel", "Cannot remove: size check failed")
            return
        }

        // Выбираем стратегию удаления в зависимости от действия
        when (blockRemoveAction) {
            BlockRemoveAction.REMOVE -> {
                Log.d("EditNoteViewModel", "Performing REMOVE")
                performBlockRemoval(index, isReFocus)
            }

            BlockRemoveAction.REMOVE_BACKSPACE -> {
                Log.d("EditNoteViewModel", "Checking REMOVE_BACKSPACE")
                if (canRemoveTextBlock(block)) {
                    Log.d("EditNoteViewModel", "Performing REMOVE_BACKSPACE")
                    performBlockRemoval(index, isReFocus)
                } else {
                    Log.d("EditNoteViewModel", "Cannot remove block with REMOVE_BACKSPACE")
                }
            }

            BlockRemoveAction.REMOVE_BACKSPACE_LIST -> {
                Log.d("EditNoteViewModel", "Performing REMOVE_BACKSPACE_LIST")
                performBlockRemoval(index, isReFocus)
            }
        }
    }

    /**
     * Проверяет, можно ли удалить текстовый блок (должен быть пустым)
     */
    private fun canRemoveTextBlock(block: Block): Boolean {
        Log.d(
            "EditNoteViewModel",
            "canRemoveTextBlock: blocks.size=${blocks.size}, isEmpty=${
                BlockUtils.isBlockEmpty(
                    block,
                )
            }, blockType=${block::class.simpleName}",
        )

        if (blocks.size <= 1) {
            Log.d("EditNoteViewModel", "Cannot remove: only one block left")
            return false
        }

        val isEmpty = BlockUtils.isBlockEmpty(block)
        Log.d("EditNoteViewModel", "Block empty check result: $isEmpty")
        return isEmpty
    }

    /**
     * Выполняет удаление блока и обновляет фокус
     */
    private fun performBlockRemoval(
        index: Int,
        isReFocus: Boolean,
    ) {

        blockOperations.removeBlock(index)
        updateUndoRedoState()
        // Обновляем фокус если требуется
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
        Log.d(
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

            // Встановлюємо фокус на заміщений блок завжди
            setLastInteractionIndex(targetBlockIndex)

            // Використовуємо метод з повторними спробами для кращої надійності
            _focusManager.setFocusWithRetry(targetBlockIndex, maxRetries = 5, delayMs = 50L)
            _focusManager.setLastInteraction(targetBlockIndex)
            updateCurrentFocusIndex(targetBlockIndex)
            Log.d("EditNoteViewModel", "Focus set to replaced block with retry: $targetBlockIndex")

            Log.d("EditNoteViewModel", "Block replaced successfully")
            true
        } else {
            Log.e(
                "EditNoteViewModel",
                "Invalid index: $targetBlockIndex. Must be between 0 and ${_blocks.size - 1}.",
            )
            false
        }
    }

    /**
     * Регистрирует изменение текста в блоке для поддержки отмены/повтора
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
     * Регистрирует изменение в блоке для поддержки отмены/повтора
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
     * Отменяет последнее действие
     */
    fun undo() {
        if (blockOperations.undo()) {
            updateUndoRedoState()
        }
    }

    /**
     * Повторяет отмененное действие
     */
    fun redo() {
        if (blockOperations.redo()) {
            updateUndoRedoState()
        }
    }

    /**
     * Обновляет состояние возможности отмены/повтора
     */
    private fun updateUndoRedoState() {
        _canUndo.value = blockOperations.canUndo()
        _canRedo.value = blockOperations.canRedo()
    }

    /**
     * Обновляет заголовок заметки в состоянии
     */
    fun updateTitle(newTitle: String) {
        _state.value = _state.value.copy(note = state.value.note.copy(title = newTitle))
    }

    // Метод для переключения режима редактирования блоков
    fun toggleBlockEditMode() {
        _isBlockEditMode.value = !_isBlockEditMode.value
    }

    fun toggleLockEditor() {
        _lockEditor.value = !_lockEditor.value
    }

    /**
     * Восстанавливает заметку из корзины
     */
    fun noteRestore() {
        val currentNote = _state.value.note
        val currentNoteId = currentNote.id

        if (currentNoteId == -1L) {
            Log.e("EditNoteViewModel", "Cannot restore note: current note ID is -1")
            return
        }

        // Получаем актуальное содержимое заметки
        val blocksJson = BlockConverter.blocksToJson(blocks)

        viewModelScope.launch {
            // Обновляем статус заметки через usecase
            val result = updateNoteTrashStatusUseCase.invoke(
                currentNote.copy(
                    value = blocksJson,
                ),
                false, // false означает "не в корзину", т.е. восстанавливаем
            )

            if (result) {
                Log.d("EditNoteViewModel", "Note restored with ID: $currentNoteId")
            } else {
                Log.e("EditNoteViewModel", "Failed to restore note with ID: $currentNoteId")
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
            Log.e("EditNoteViewModel", "Error duplicating note: ${e.message}")
            -1L
        }
    }

    /**
     * Удаляет заметку полностью из базы данных
     */
    fun noteDelete() {
        val currentNoteId = _state.value.note.id

        if (currentNoteId == -1L) {
            Log.e("EditNoteViewModel", "Cannot delete note: current note ID is -1")
            return
        }

        viewModelScope.launch {
            try {
                // Видаляємо нотатку з бази даних
                notesRepository.deleteNoteById(currentNoteId)
                Log.d("EditNoteViewModel", "Note deleted with ID: $currentNoteId")
            } catch (e: Exception) {
                Log.e("EditNoteViewModel", "Error deleting note: ${e.message}")
            }
        }
    }

    fun updateTagNote(tagId: Long) {
        val currentNote = _state.value.note

        // Обновляем состояние UI немедленно для отзывчивости интерфейса
        _state.value = _state.value.copy(
            note = currentNote.copy(tagId = tagId),
        )

        // Применяем изменение в БД, если заметка уже сохранена
        if (currentNote.id != -1L) {
            viewModelScope.launch {
                updateNoteTagUseCase.invoke(currentNote, tagId)
            }
        }
        // Если заметка новая, тег будет сохранен вместе с заметкой при следующем сохранении
    }

    fun updateFontSize(size: Int) {
        fontSizeUseCase.setFontSize(size, viewModelScope)
    }

    fun updateFontStyle(style: FontStyleType) {
        fontStyleUseCase.setFontStyle(style, viewModelScope)
    }

    fun moveBlock(up: Boolean) { // up = true - вверх, false - вниз
        val fromIndex = _focusManager.lastInteractionIndex.value
        val toIndex = if (up) fromIndex - 1 else fromIndex + 1

        if (fromIndex !in _blocks.indices || toIndex !in _blocks.indices) return

        blockOperations.moveBlock(fromIndex, toIndex)
        updateUndoRedoState()

        // Обновляем фокус после перемещения блока
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

