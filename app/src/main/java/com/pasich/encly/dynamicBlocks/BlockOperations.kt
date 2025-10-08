package com.pasich.encly.dynamicBlocks

import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Класс для управления операциями над блоками с поддержкой отмены/повтора действий.
 * Поддерживает отмену/повтор изменений структуры блоков и изменений контента внутри блоков.
 */
class BlockOperations(
    private val blocks: SnapshotStateList<Block>,
) {
    private val undoStack = mutableListOf<Operation>()
    private val redoStack = mutableListOf<Operation>()

    /**
     * Добавляет новый блок по указанному индексу.
     * @param index индекс для добавления нового блока
     * @param block новый блок
     */
    fun addBlock(
        index: Int,
        block: Block,
    ) {
        val operation = AddOperation(index, block)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear() // После новой операции очищаем стек повтора
    }

    /**
     * Удаляет блок по указанному индексу.
     * @param index индекс удаляемого блока
     */
    fun removeBlock(index: Int) {
        if (index !in blocks.indices) return

        val removedBlock = blocks[index]
        val operation = RemoveOperation(index, removedBlock)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Перемещает блок с одного индекса на другой.
     * @param fromIndex начальный индекс
     * @param toIndex конечный индекс
     */
    fun moveBlock(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in blocks.indices || toIndex !in blocks.indices) return

        val operation = MoveOperation(fromIndex, toIndex)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Заменяет блок по указанному индексу новым блоком.
     * @param index индекс заменяемого блока
     * @param newBlock новый блок
     */
    fun replaceBlock(
        index: Int,
        newBlock: Block,
    ) {
        if (index !in blocks.indices) return

        val oldBlock = blocks[index]
        val operation = ReplaceOperation(index, oldBlock, newBlock)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Отменяет последнюю операцию.
     * @return true если операция была отменена, иначе false
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false

        val operation = undoStack.removeAt(undoStack.size - 1)
        val inverseOperation = operation.createInverse()
        executeOperation(inverseOperation)
        redoStack.add(operation)
        return true
    }

    /**
     * Повторяет ранее отмененную операцию.
     * @return true если операция была повторена, иначе false
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false

        val operation = redoStack.removeAt(redoStack.size - 1)
        executeOperation(operation)
        undoStack.add(operation)
        return true
    }

    /**
     * Выполняет операцию над списком блоков.
     */
    private fun executeOperation(operation: Operation) {
        when (operation) {
            is AddOperation -> blocks.add(operation.index, operation.block)
            is RemoveOperation -> blocks.removeAt(operation.index)
            is MoveOperation -> {
                val block = blocks.removeAt(operation.fromIndex)
                blocks.add(operation.toIndex, block)
            }
            is ReplaceOperation -> blocks[operation.index] = operation.newBlock
            is TextChangeOperation -> operation.apply()
            is ContentChangeOperation -> operation.apply()
        }
    }

    /**
     * Получает текущее состояние блоков.
     */
    fun getCurrentBlocks(): List<Block> = blocks.toList()

    /**
     * Заменяет все блоки на новый список.
     */
    fun setBlocks(newBlocks: List<Block>) {
        blocks.clear()
        blocks.addAll(newBlocks)
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Проверяет, можно ли отменить операцию.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Проверяет, можно ли повторить операцию.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Регистрирует изменение текста в блоке.
     * @param index индекс блока
     * @param oldText старый текст
     * @param newText новый текст
     */
    fun registerTextChange(
        index: Int,
        oldText: String,
        newText: String,
    ) {
        if (index !in blocks.indices || oldText == newText) return

        val operation = TextChangeOperation(index, oldText, newText)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Регистрирует изменения в блоке любого типа
     * @param index индекс блока
     * @param oldBlock старое состояние блока
     * @param newBlock новое состояние блока
     */
    fun registerContentChange(
        index: Int,
        oldBlock: Block,
        newBlock: Block,
    ) {
        if (index !in blocks.indices) return

        val operation = ContentChangeOperation(index, oldBlock, newBlock)
        undoStack.add(operation)
        redoStack.clear()
    }

    // Внутренние классы операций

    /**
     * Базовый интерфейс для всех операций.
     */
    private interface Operation {
        fun createInverse(): Operation
    }

    /**
     * Операция добавления блока.
     */
    private inner class AddOperation(
        val index: Int,
        val block: Block,
    ) : Operation {
        override fun createInverse(): Operation = RemoveOperation(index, block)
    }

    /**
     * Операция удаления блока.
     */
    private inner class RemoveOperation(
        val index: Int,
        val block: Block,
    ) : Operation {
        override fun createInverse(): Operation = AddOperation(index, block)
    }

    /**
     * Операция перемещения блока.
     */
    private inner class MoveOperation(
        val fromIndex: Int,
        val toIndex: Int,
    ) : Operation {
        override fun createInverse(): Operation = MoveOperation(toIndex, fromIndex)
    }

    /**
     * Операция замены блока.
     */
    private inner class ReplaceOperation(
        val index: Int,
        val oldBlock: Block,
        val newBlock: Block,
    ) : Operation {
        override fun createInverse(): Operation = ReplaceOperation(index, newBlock, oldBlock)
    }

    /**
     * Операция изменения текста в блоке.
     */
    private inner class TextChangeOperation(
        val index: Int,
        val oldText: String,
        val newText: String,
    ) : Operation {
        override fun createInverse(): Operation = TextChangeOperation(index, newText, oldText)

        /**
         * Выполняет операцию изменения текста.
         * Использует интерфейс TextualBlock для унифицированной обработки текста
         */
        fun apply() {
            if (index !in blocks.indices) return

            val block = blocks[index]
            if (block is TextualBlock) {
                block.text.value = newText
            }
        }
    }

    /**
     * Операция изменения содержимого блока.
     * Используется для более сложных блоков, где нужно сохранить всё состояние.
     */
    private inner class ContentChangeOperation(
        val index: Int,
        val oldBlock: Block,
        val newBlock: Block,
    ) : Operation {
        override fun createInverse(): Operation = ContentChangeOperation(index, newBlock, oldBlock)

        /**
         * Выполняет операцию изменения содержимого.
         * Полностью заменяет блок на новый.
         */
        fun apply() {
            if (index !in blocks.indices) return
            blocks[index] = newBlock
        }
    }
}

/**
 * Расширение для создания BlockOperations из списка блоков.
 */
fun SnapshotStateList<Block>.toBlockOperations(): BlockOperations = BlockOperations(this)
