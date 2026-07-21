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
 * Centralized focus manager for the block editor.
 * Manages focus, navigation between blocks, and state synchronization.
 */
class CentralizedFocusManager {
    // Map of FocusRequesters for each block
    private val focusRequesters = mutableStateMapOf<Int, FocusRequester>()

    // Current index of the focused block
    private val _currentFocusIndex = MutableStateFlow(-1)
    val currentFocusIndex: StateFlow<Int> = _currentFocusIndex.asStateFlow()

    // The last block the user interacted with
    private val _lastInteractionIndex = MutableStateFlow(0)
    val lastInteractionIndex: StateFlow<Int> = _lastInteractionIndex.asStateFlow()

    // Flag indicating whether focus should be ignored (for some block types)
    private val _shouldIgnoreFocus = mutableStateOf(false)
    val shouldIgnoreFocus: Boolean get() = _shouldIgnoreFocus.value

    // Owned scope for deferred focus work; cancelled in dispose() so retry coroutines
    // don't outlive the owning ViewModel (avoids a leak / focus mutation after teardown).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Cancels any in-flight focus coroutines. Call from the owner's onCleared(). */
    fun dispose() {
        scope.cancel()
    }

    // Callback for moving the cursor to the end of the text
    private val cursorToEndCallbacks = mutableStateMapOf<Int, () -> Unit>()

    /**
     * Registers a FocusRequester for a block.
     */
    fun registerFocusRequester(
        index: Int,
        focusRequester: FocusRequester,
    ) {
        focusRequesters[index] = focusRequester
    }

    /**
     * Registers a callback for moving the cursor to the end of the text.
     */
    fun registerCursorToEndCallback(
        index: Int,
        callback: () -> Unit,
    ) {
        cursorToEndCallbacks[index] = callback
    }

    /**
     * Removes the FocusRequester for a block.
     */
    fun unregisterFocusRequester(index: Int) {
        focusRequesters.remove(index)
        cursorToEndCallbacks.remove(index)
    }

    /**
     * Checks whether a block can receive focus (whether it has an editable text field).
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
                // LinkBlock can receive focus only if the URL is empty (input mode)
                try {
                    // Access the block field via reflection
                    val blockField = block.javaClass.getDeclaredField("block")
                    blockField.isAccessible = true
                    val blockValue = blockField.get(block)

                    // Get the MutableStateFlow
                    val valueMethod = blockValue.javaClass.getMethod("getValue")
                    val linkDataBlock = valueMethod.invoke(blockValue)

                    // Check whether the URL is empty
                    val urlField = linkDataBlock.javaClass.getDeclaredField("url")
                    urlField.isAccessible = true
                    val url = urlField.get(linkDataBlock) as? String

                    url?.isBlank() == true
                } catch (e: Exception) {
                    AppLogger.w("CentralizedFocusManager", "Failed to check LinkBlock focusability: ${e.message}")
                    false
                }
            }
            "ListBlock" -> true // ListBlock has its own focus logic
            else -> false // SeparatorBlock, ImageBlock, etc. cannot receive focus
        }
    }

    /**
     * Finds the previous block that can receive focus.
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
        // If no previous focusable block was found, return the first available one
        for (i in 0 until blocks.size) {
            if (isBlockFocusable(i, blocks)) {
                return i
            }
        }
        return 0 // Fallback
    }

    /**
     * Sets focus on the block with the given index.
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

        // Prevent recursion - do not set focus if it is already on this index
        if (_currentFocusIndex.value == index && !ignore) {
            AppLogger.d("CentralizedFocusManager", "setFocus: focus already on index $index")
            return
        }

        _currentFocusIndex.value = index
        _shouldIgnoreFocus.value = ignore

        if (!ignore) {
            AppLogger.d("CentralizedFocusManager", "setFocus: requesting focus for index $index")
            requestFocusInternal(index)

            // Move the cursor to the end of the text if needed
            if (moveCursorToEnd) {
                cursorToEndCallbacks[index]?.invoke()
                AppLogger.d("CentralizedFocusManager", "setFocus: moved cursor to end for index $index")
            }
        } else {
            AppLogger.d("CentralizedFocusManager", "setFocus: ignoring focus request for index $index")
        }
    }

    /**
     * Sets the last interaction index.
     */
    fun setLastInteraction(index: Int) {
        if (index >= 0) {
            _lastInteractionIndex.value = index
        }
    }

    /**
     * Updates the current focus index without calling requestFocus (to avoid recursion).
     */
    fun updateCurrentFocusIndex(index: Int) {
        if (index >= 0) {
            _currentFocusIndex.value = index
        }
    }

    /**
     * Navigation to the next block.
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
     * Navigation to the previous block.
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
     * Clears focus.
     */
    fun clearFocus() {
        _currentFocusIndex.value = -1
        _shouldIgnoreFocus.value = false
    }

    /**
     * Checks whether the FocusRequester for a block is ready.
     */
    fun isFocusReady(index: Int): Boolean = focusRequesters.containsKey(index)

    /**
     * Internal method for requesting focus.
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
     * Clears all data.
     */
    fun cleanup() {
        focusRequesters.clear()
        _currentFocusIndex.value = -1
        _lastInteractionIndex.value = 0
        _shouldIgnoreFocus.value = false
    }

    /**
     * Deferred setting of focus on the block with the given index.
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

        // Use a coroutine for deferred execution
        scope.launch {
            delay(delayMillis)
            setFocus(index, ignore)
            AppLogger.d("CentralizedFocusManager", "delayedSetFocus: focus set to index $index after delay")
        }
    }

    /**
     * Sets focus on the block with the given index, with a delay and retry attempts.
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

        scope.launch {
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
