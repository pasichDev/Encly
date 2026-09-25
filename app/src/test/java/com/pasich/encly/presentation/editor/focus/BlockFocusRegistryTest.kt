package com.pasich.encly.presentation.editor.focus

import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.state.FocusRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockFocusRegistryTest {
    private val blocks = mutableListOf<Block>(Block.TextBlock(), Block.SeparatorBlock(), Block.TextBlock())
    private val registry = BlockFocusRegistry { blocks }

    @Test
    fun focusIsRequestedEveryTimeEvenForTheSameBlock() {
        var requests = 0
        val carets = mutableListOf<Int>()
        registry.registerFocusTarget(blocks[0].id) { requests++ }
        registry.registerCaret(blocks[0].id) { carets += it }

        assertTrue(registry.focus(blocks[0].id))
        assertTrue(registry.focus(blocks[0].id, cursorToEnd = true))
        assertTrue(registry.focus(FocusRequest(blocks[0].id, caret = 3)))

        assertEquals(3, requests)
        assertEquals(listOf(Int.MAX_VALUE, 3), carets)
    }

    @Test
    fun aListIsFocusedAtItsStartItsEndOrANamedItem() {
        val asked = mutableListOf<FocusRequest>()
        registry.registerFieldsFocusTarget(blocks[0].id) { request -> asked += request }

        registry.focus(blocks[0].id)
        registry.focus(blocks[0].id, cursorToEnd = true)
        registry.focus(FocusRequest(blocks[0].id, caret = 2, itemId = "item"))

        assertEquals(listOf(false, true, false), asked.map { it.cursorToEnd })
        assertEquals("item", asked.last().itemId)
        assertEquals(2, asked.last().caretOffset)
    }

    @Test
    fun aBlockOffScreenIsScrolledToBeforeItIsFocused() = runTest {
        var requests = 0
        val scrolledTo = mutableListOf<String>()

        registry.focusWhenComposed(FocusRequest(blocks[2].id)) { id ->
            scrolledTo += id
            registry.registerFocusTarget(id) { requests++ } // composed once on screen
        }

        assertEquals(listOf(blocks[2].id), scrolledTo)
        assertEquals(1, requests)
    }

    @Test
    fun aBlockWithoutAComposedFieldIsNotFocused() {
        assertFalse(registry.focus(blocks[2].id))
    }

    @Test
    fun anUnregisteredTargetIsGoneButAStaleUnregisterKeepsTheNewerOne() {
        var old = 0
        var new = 0
        val unregisterOld = registry.registerFocusTarget(blocks[0].id) { old++ }
        registry.registerFocusTarget(blocks[0].id) { new++ }

        unregisterOld() // the disposed composable of the old field
        registry.focus(blocks[0].id)

        assertEquals(0, old)
        assertEquals(1, new)
    }

    @Test
    fun aFailingFocusRequestDoesNotCrashTheEditor() {
        registry.registerFocusTarget(blocks[0].id) { error("FocusRequester is not initialized") }

        assertTrue(registry.focus(blocks[0].id))
    }

    @Test
    fun aRequestWaitsUntilTheNewBlockIsComposed() = runTest {
        val added = Block.TextBlock()
        blocks.add(added)
        var requests = 0

        val job = launch { registry.focusWhenComposed(FocusRequest(added.id)) }
        runCurrent()
        assertEquals(0, requests)

        registry.registerFocusTarget(added.id) { requests++ } // composed a frame later
        advanceUntilIdle()

        assertEquals(1, requests)
        assertTrue(job.isCompleted)
    }

    @Test
    fun navigationFindsTheNextBlockAndTheFocusableBlockAbove() {
        assertSame(blocks[1], registry.nextBlock(blocks[0].id))
        assertNull(registry.nextBlock(blocks[2].id))

        // The separator in between cannot take focus.
        assertSame(blocks[0], registry.previousFocusableBlock(blocks[2].id))
        assertNull(registry.previousFocusableBlock(blocks[0].id))
    }

    @Test
    fun aSavedLinkIsSkippedWhenMovingUp() {
        blocks[1] = Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "https://a.b")))

        assertSame(blocks[0], registry.previousFocusableBlock(blocks[2].id))
    }
}
