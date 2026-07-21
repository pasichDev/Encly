package com.pasich.encly.dynamicBlocks.focus

import com.pasich.encly.core.AppLogger
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Централізований менеджер фокуса для блочного редактора
 * Управляє фокусом, навігацією між блоками та синхронізацією станів
 */
class CentralizedFocusManager {
    // Карта FocusRequester'ів для кожного блока
    private val focusRequesters = mutableStateMapOf<Int, FocusRequester>()

    // Поточний індекс блока з фокусом
    private val _currentFocusIndex = MutableStateFlow(-1)
    val currentFocusIndex: StateFlow<Int> = _currentFocusIndex.asStateFlow()

    // Останній блок, з яким взаємодіяв користувач
    private val _lastInteractionIndex = MutableStateFlow(0)
    val lastInteractionIndex: StateFlow<Int> = _lastInteractionIndex.asStateFlow()

    // Прапорець, чи потрібно ігнорувати фокус (для деяких типів блоків)
    private val _shouldIgnoreFocus = mutableStateOf(false)
    val shouldIgnoreFocus: Boolean get() = _shouldIgnoreFocus.value

    // Callback для встановлення курсора в кінець тексту
    private val cursorToEndCallbacks = mutableStateMapOf<Int, () -> Unit>()

    /**
     * Реєструє FocusRequester для блока
     */
    fun registerFocusRequester(
        index: Int,
        focusRequester: FocusRequester,
    ) {
        focusRequesters[index] = focusRequester
    }

    /**
     * Реєструє callback для встановлення курсора в кінець тексту
     */
    fun registerCursorToEndCallback(
        index: Int,
        callback: () -> Unit,
    ) {
        cursorToEndCallbacks[index] = callback
    }

    /**
     * Видаляє FocusRequester для блока
     */
    fun unregisterFocusRequester(index: Int) {
        focusRequesters.remove(index)
        cursorToEndCallbacks.remove(index)
    }

    /**
     * Перевіряє, чи може блок отримати фокус (чи є text field який можна редагувати)
     */
    fun isBlockFocusable(
        index: Int,
        blocks: List<Any>,
    ): Boolean {
        if (index !in blocks.indices) return false

        val block = blocks[index]
        return when (block::class.java.simpleName) {
            "TextBlock", "QuoteBlock", "HBlock" -> true
            "LinkBlock" -> {
                // LinkBlock може отримати фокус тільки якщо URL порожній (режим введення)
                try {
                    // Отримуємо доступ до поля block через рефлексію
                    val blockField = block.javaClass.getDeclaredField("block")
                    blockField.isAccessible = true
                    val blockValue = blockField.get(block)

                    // Отримуємо MutableStateFlow
                    val valueMethod = blockValue.javaClass.getMethod("getValue")
                    val linkDataBlock = valueMethod.invoke(blockValue)

                    // Перевіряємо чи URL порожній
                    val urlField = linkDataBlock.javaClass.getDeclaredField("url")
                    urlField.isAccessible = true
                    val url = urlField.get(linkDataBlock) as? String

                    url?.isBlank() == true
                } catch (e: Exception) {
                    AppLogger.w("CentralizedFocusManager", "Failed to check LinkBlock focusability: ${e.message}")
                    false
                }
            }
            "ListBlock" -> true // ListBlock має власну логіку фокуса
            else -> false // SeparatorBlock, ImageBlock тощо не можуть отримати фокус
        }
    }

    /**
     * Знаходить попередній блок, який може отримати фокус
     */
    fun findPreviousFocusableBlock(
        currentIndex: Int,
        blocks: List<Any>,
    ): Int {
        for (i in (currentIndex - 1) downTo 0) {
            if (isBlockFocusable(i, blocks)) {
                return i
            }
        }
        // Якщо не знайшли попереднього фокусабельного блока, повертаємо перший доступний
        for (i in 0 until blocks.size) {
            if (isBlockFocusable(i, blocks)) {
                return i
            }
        }
        return 0 // Fallback
    }

    /**
     * Встановлює фокус на блок з заданим індексом
     */
    fun setFocus(
        index: Int,
        ignore: Boolean = false,
        moveCursorToEnd: Boolean = false,
    ) {
        AppLogger.d(
            "CentralizedFocusManager",
            "setFocus called: index=$index, ignore=$ignore, moveCursorToEnd=$moveCursorToEnd, currentFocus=${_currentFocusIndex.value}",
        )

        if (index < 0) {
            AppLogger.d("CentralizedFocusManager", "setFocus: invalid index $index")
            return
        }

        // Предотвращаем рекурсию - не устанавливаем фокус если он уже на этом индексе
        if (_currentFocusIndex.value == index && !ignore) {
            AppLogger.d("CentralizedFocusManager", "setFocus: focus already on index $index")
            return
        }

        _currentFocusIndex.value = index
        _shouldIgnoreFocus.value = ignore

        if (!ignore) {
            AppLogger.d("CentralizedFocusManager", "setFocus: requesting focus for index $index")
            requestFocusInternal(index)

            // Встановлюємо курсор в кінець тексту якщо потрібно
            if (moveCursorToEnd) {
                cursorToEndCallbacks[index]?.invoke()
                AppLogger.d("CentralizedFocusManager", "setFocus: moved cursor to end for index $index")
            }
        } else {
            AppLogger.d("CentralizedFocusManager", "setFocus: ignoring focus request for index $index")
        }
    }

    /**
     * Встановлює останній індекс взаємодії
     */
    fun setLastInteraction(index: Int) {
        if (index >= 0) {
            _lastInteractionIndex.value = index
        }
    }

    /**
     * Обновляет текущий индекс фокуса без вызова requestFocus (для избежания рекурсии)
     */
    fun updateCurrentFocusIndex(index: Int) {
        if (index >= 0) {
            _currentFocusIndex.value = index
        }
    }

    /**
     * Навігація до наступного блока
     */
    fun moveToNext(totalBlocks: Int): Boolean {
        val current = _currentFocusIndex.value
        val next = (current + 1).coerceAtMost(totalBlocks - 1)

        AppLogger.d("CentralizedFocusManager", "moveToNext: current=$current, next=$next, totalBlocks=$totalBlocks")

        if (next != current) {
            setFocus(next)
            setLastInteraction(next)
            AppLogger.d("CentralizedFocusManager", "moveToNext: success, moved to $next")
            return true
        }
        AppLogger.d("CentralizedFocusManager", "moveToNext: failed, already at last block")
        return false
    }

    /**
     * Навігація до попереднього блока
     */
    fun moveToPrevious(blocks: List<Any>? = null): Boolean {
        val current = _currentFocusIndex.value
        val previous =
            if (blocks != null) {
                findPreviousFocusableBlock(current, blocks)
            } else {
                (current - 1).coerceAtLeast(0)
            }

        if (previous != current) {
            setFocus(previous, moveCursorToEnd = true)
            setLastInteraction(previous)
            return true
        }
        return false
    }

    /**
     * Очищає фокус
     */
    fun clearFocus() {
        _currentFocusIndex.value = -1
        _shouldIgnoreFocus.value = false
    }

    /**
     * Перевіряє, чи готовий FocusRequester для блока
     */
    fun isFocusReady(index: Int): Boolean = focusRequesters.containsKey(index)

    /**
     * Внутрішній метод для запиту фокуса
     */
    private fun requestFocusInternal(index: Int) {
        AppLogger.d("CentralizedFocusManager", "requestFocusInternal: index=$index, hasFocusRequester=${focusRequesters.containsKey(index)}")
        focusRequesters[index]?.let { focusRequester ->
            try {
                AppLogger.d("CentralizedFocusManager", "requestFocusInternal: requesting focus for index $index")
                focusRequester.requestFocus()
                AppLogger.d("CentralizedFocusManager", "requestFocusInternal: focus request completed for index $index")
            } catch (e: Exception) {
                AppLogger.e("CentralizedFocusManager", "requestFocusInternal: error requesting focus for index $index: ${e.message}")
            }
        } ?: run {
            AppLogger.w("CentralizedFocusManager", "requestFocusInternal: no FocusRequester found for index $index")
        }
    }

    /**
     * Очищає всі дані
     */
    fun cleanup() {
        focusRequesters.clear()
        _currentFocusIndex.value = -1
        _lastInteractionIndex.value = 0
        _shouldIgnoreFocus.value = false
    }

    /**
     * Відкладене встановлення фокуса на блок з заданим індексом
     */
    fun delayedSetFocus(
        index: Int,
        delayMillis: Long,
        ignore: Boolean = false,
    ) {
        AppLogger.d("CentralizedFocusManager", "delayedSetFocus called: index=$index, delay=$delayMillis, ignore=$ignore")

        if (index < 0) {
            AppLogger.d("CentralizedFocusManager", "delayedSetFocus: invalid index $index")
            return
        }

        // Використовуємо корутину для відкладеного виконання
        CoroutineScope(Dispatchers.Main).launch {
            delay(delayMillis)
            setFocus(index, ignore)
            AppLogger.d("CentralizedFocusManager", "delayedSetFocus: focus set to index $index after delay")
        }
    }

    /**
     * Встановлює фокус на блок з заданим індексом з відкладенням та повторними спробами
     */
    fun setFocusWithRetry(
        index: Int,
        ignore: Boolean = false,
        maxRetries: Int = 3,
        delayMs: Long = 100L,
    ) {
        AppLogger.d("CentralizedFocusManager", "setFocusWithRetry called: index=$index, ignore=$ignore, maxRetries=$maxRetries")

        if (index < 0) {
            AppLogger.d("CentralizedFocusManager", "setFocusWithRetry: invalid index $index")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            var attempts = 0
            while (attempts < maxRetries) {
                AppLogger.d("CentralizedFocusManager", "setFocusWithRetry: attempt ${attempts + 1}/$maxRetries for index $index")

                if (focusRequesters.containsKey(index)) {
                    AppLogger.d("CentralizedFocusManager", "setFocusWithRetry: FocusRequester found, setting focus")
                    setFocus(index, ignore)
                    break
                } else {
                    AppLogger.d("CentralizedFocusManager", "setFocusWithRetry: FocusRequester not ready, waiting...")
                    delay(delayMs)
                    attempts++
                }
            }

            if (attempts >= maxRetries) {
                AppLogger.w("CentralizedFocusManager", "setFocusWithRetry: failed to set focus after $maxRetries attempts for index $index")
            }
        }
    }
}
