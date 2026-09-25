package com.pasich.encly.presentation.editor.persistence

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The editor's autosave: one write per pause in typing, never one per keystroke. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutosaveDebounceTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository().apply { insertResult = INSERTED_ID }
    private val body = MutableStateFlow("")
    private val persistence = NotePersistence(
        notesRepository = repository,
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        ioDispatcher = dispatcher,
        readOnly = false,
        blocks = { listOf(Block.TextBlock(body)) },
        copyTitle = { it },
    )

    @Test
    fun steadyTypingKeepsPushingTheSaveBack() = runTest(dispatcher) {
        persistence.startAutosave(backgroundScope, body.map { })

        "Groceries".forEachIndexed { index, _ ->
            body.value = "Groceries".take(index + 1)
            advanceTimeBy(KEYSTROKE_MS)
            runCurrent()
        }
        assertEquals("nothing while the user types", 0, repository.insertCalls)

        passQuietTime()

        assertEquals(1, repository.insertCalls)
        assertEquals(json("Groceries"), repository.insertedNotes.single().value)
    }

    @Test
    fun eachPauseSavesTheTextTypedBeforeIt() = runTest(dispatcher) {
        persistence.startAutosave(backgroundScope, body.map { })

        body.value = "first"
        passQuietTime()
        body.value = "first, second"
        passQuietTime()

        assertEquals(1, repository.insertCalls)
        assertEquals(json("first, second"), repository.updatedNotes.single().value)
    }

    @Test
    fun aPauseWithoutChangesWritesNothing() = runTest(dispatcher) {
        persistence.startAutosave(backgroundScope, body.map { })
        body.value = "saved"
        passQuietTime()

        body.value = "saved!"
        runCurrent()
        body.value = "saved"
        passQuietTime()

        assertEquals(1, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun aTitleEditAloneIsAutosavedAfterThePause() = runTest(dispatcher) {
        persistence.startAutosave(backgroundScope, body.map { })

        persistence.updateNote { it.copy(title = "Trip") }
        advanceTimeBy(AUTOSAVE_DEBOUNCE_MS / 2)
        persistence.updateNote { it.copy(title = "Trip to Lviv") }
        advanceTimeBy(AUTOSAVE_DEBOUNCE_MS / 2 + 1)
        runCurrent()
        assertEquals(0, repository.insertCalls)

        passQuietTime()

        assertEquals("Trip to Lviv", repository.insertedNotes.single().title)
    }

    private fun TestScope.passQuietTime() {
        advanceTimeBy(AUTOSAVE_DEBOUNCE_MS + 1)
        runCurrent()
    }

    private fun json(text: String) = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow(text))))

    private companion object {
        const val INSERTED_ID = 73L
        const val KEYSTROKE_MS = 300L
    }
}
