package com.pasich.encly.presentation.editor.persistence

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotePersistenceTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository().apply { insertResult = INSERTED_ID }
    private var blocks: List<Block> = listOf(Block.TextBlock())

    // --- saving -------------------------------------------------------------------------------

    @Test
    fun aNewNoteIsInsertedOnceAndThenUpdated() = runTest(dispatcher) {
        val persistence = persistence()
        persistence.updateNote { it.copy(title = "draft") }

        assertTrue(persistence.save())
        assertEquals(INSERTED_ID, persistence.state.value.note.id)
        assertTrue(persistence.save())

        assertEquals(1, repository.insertCalls)
        assertEquals(1, repository.updateCalls)
        assertEquals(SaveStatusNote.SAVED, persistence.status.value)
    }

    @Test
    fun aBlankNewNoteIsNotStored() = runTest(dispatcher) {
        val persistence = persistence()
        blocks = listOf(text("  \n "), Block.LinkBlock())

        assertTrue(persistence.save())

        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun aFailedInsertIsReportedAndLeavesTheNoteNew() = runTest(dispatcher) {
        repository.insertResult = 0L
        val persistence = persistence()
        persistence.updateNote { it.copy(title = "draft") }

        assertFalse(persistence.save())

        assertEquals(-1L, persistence.state.value.note.id)
        // The editor keeps saying so until a save succeeds.
        assertEquals(SaveStatusNote.FAILED, persistence.status.value)
        repository.insertResult = INSERTED_ID
        assertTrue(persistence.save())
        assertEquals(SaveStatusNote.SAVED, persistence.status.value)
    }

    @Test
    fun aSavedEditMovesTheShownDateAndDropsAnImportedSummary() = runTest(dispatcher) {
        repository.allNotesWithTags.value = listOf(
            NoteWithTag(
                note = Note(
                    id = NOTE_ID,
                    title = "title",
                    value = BlockConverter.blocksToJson(listOf(text("body"))),
                    description = "imported summary",
                    date = OLD_DATE,
                ),
                tag = null,
            ),
        )
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }
        blocks = listOf(text("body, edited"))

        assertTrue(persistence.save())

        val stored = repository.updatedNotes.single()
        assertTrue(stored.date > OLD_DATE)
        assertEquals("", stored.description)
        assertEquals(stored.date, persistence.state.value.note.date)
    }

    @Test
    fun anUnchangedNoteKeepsItsDateAndSummary() = runTest(dispatcher) {
        repository.allNotesWithTags.value = listOf(
            NoteWithTag(
                note = Note(
                    id = NOTE_ID,
                    title = "title",
                    value = BlockConverter.blocksToJson(listOf(text("body"))),
                    description = "imported summary",
                    date = OLD_DATE,
                ),
                tag = null,
            ),
        )
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }

        assertTrue(persistence.save())

        assertEquals(OLD_DATE, repository.updatedNotes.single().date)
        assertEquals("imported summary", repository.updatedNotes.single().description)
    }

    @Test
    fun changesAreWhatDiscardWouldUndo() = runTest(dispatcher) {
        storeNote(text("body"))
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }
        assertFalse(persistence.hasChanges())

        blocks = listOf(text("body, edited"))
        assertTrue(persistence.hasChanges())

        blocks = listOf(text("body"))
        persistence.updateNote { it.copy(tagId = TAG_ID) }
        assertTrue(persistence.hasChanges())
    }

    @Test
    fun aNewNoteWithoutTitleOrContentIsABlankDraft() = runTest(dispatcher) {
        val persistence = persistence()
        assertTrue(persistence.isBlankDraft)

        blocks = listOf(text("x"))
        assertFalse(persistence.isBlankDraft)
    }

    @Test
    fun nothingIsSavedWhileTheNoteIsLoading() = runTest(dispatcher) {
        val persistence = persistence(loading = true)
        persistence.updateNote { it.copy(title = "typed before the note arrived") }

        assertTrue(persistence.save())

        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun aReadOnlyNoteIsNeverSaved() = runTest(dispatcher) {
        storeNote(text("body"))
        val persistence = persistence(loading = true, readOnly = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }
        blocks = listOf(text("edited"))

        assertTrue(persistence.save())

        assertEquals(0, repository.updateCalls)
    }

    // --- loading ------------------------------------------------------------------------------

    @Test
    fun loadingShowsTheBlocksAndKeepsTheNoteId() = runTest(dispatcher) {
        storeNote(text("body"))
        val persistence = persistence(loading = true)

        assertTrue(persistence.load(NOTE_ID, isCopy = false) { blocks = it })

        assertEquals("body", (blocks.single() as Block.TextBlock).text.value)
        assertEquals(NOTE_ID, persistence.state.value.note.id)
        assertEquals(SaveStatusNote.OLD, persistence.status.value)
    }

    @Test
    fun aCopyIsANewUntaggedNote() = runTest(dispatcher) {
        storeNote(text("body"))
        val persistence = persistence(loading = true)

        persistence.load(NOTE_ID, isCopy = true) { blocks = it }

        assertEquals(-1L, persistence.state.value.note.id)
        assertEquals("title (копія)", persistence.state.value.note.title)
        assertNull(persistence.state.value.note.tagId)
    }

    @Test
    fun aMissingNoteIsReportedAndNeverSaved() = runTest(dispatcher) {
        val persistence = persistence(loading = true)

        assertFalse(persistence.load(NOTE_ID, isCopy = false) { blocks = it })
        persistence.updateNote { it.copy(title = "x") }
        persistence.save()

        assertTrue(persistence.contentLoadFailed.value)
        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun unreadableContentIsNotOverwritten() = runTest(dispatcher) {
        repository.allNotesWithTags.value =
            listOf(NoteWithTag(note = Note(id = NOTE_ID, title = "t", value = "{not json"), tag = null))
        val persistence = persistence(loading = true)

        // Reported as not loaded, so the editor stays read-only; the title is still shown.
        assertFalse(persistence.load(NOTE_ID, isCopy = false) { blocks = it })
        assertEquals("t", persistence.state.value.note.title)
        blocks = listOf(text("edited"))
        persistence.save()

        assertTrue(persistence.contentLoadFailed.value)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun aCopyOfUnreadableContentIsReportedAndNeverInserted() = runTest(dispatcher) {
        repository.allNotesWithTags.value =
            listOf(NoteWithTag(note = Note(id = NOTE_ID, title = "t", value = "{not json"), tag = null))
        val persistence = persistence(loading = true)

        assertFalse(persistence.load(NOTE_ID, isCopy = true) { blocks = it })
        blocks = listOf(text("typed"))
        persistence.save()

        assertEquals(0, repository.insertCalls)
    }

    // --- closing: trash, discard, delete ------------------------------------------------------

    @Test
    fun aSaveAfterTrashCannotBringTheNoteBack() = runTest(dispatcher) {
        storeNote(text("original"))
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }
        blocks = listOf(text("edited"))

        assertTrue(persistence.moveToTrash())
        persistence.save()

        assertEquals(1, repository.updateCalls)
        assertTrue(repository.updatedNotes.single().isTrash)
        assertTrue(persistence.state.value.note.isTrash)
    }

    @Test
    fun restoringTakesTheNoteOutOfTheTrashWithTheEditorContent() = runTest(dispatcher) {
        storeNote(text("original"), isTrash = true)
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }

        assertTrue(persistence.restoreFromTrash())

        val restored = repository.updatedNotes.single()
        assertFalse(restored.isTrash)
        assertEquals(BlockConverter.blocksToJson(blocks), restored.value)
    }

    @Test
    fun discardPutsBackTheOpenedNoteAndBlocksLaterSaves() = runTest(dispatcher) {
        storeNote(text("original"))
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }
        blocks = listOf(text("edited"))

        assertTrue(persistence.discard())
        persistence.save()

        assertEquals(BlockConverter.blocksToJson(listOf(text("original"))), repository.updatedNotes.single().value)
    }

    @Test
    fun discardingAnAutosavedNewNoteDeletesIt() = runTest(dispatcher) {
        val persistence = persistence()
        persistence.updateNote { it.copy(title = "draft") }
        persistence.save()

        assertTrue(persistence.discard())

        assertEquals(listOf(INSERTED_ID), repository.deletedIds)
    }

    @Test
    fun deletingANewNoteTouchesNothing() = runTest(dispatcher) {
        val persistence = persistence()

        assertTrue(persistence.delete())

        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun aDuplicateIsStoredAsACopy() = runTest(dispatcher) {
        val persistence = persistence()
        persistence.updateNote { it.copy(title = "note") }

        assertEquals(INSERTED_ID, persistence.duplicate())
        assertEquals("note (копія)", repository.insertedNotes.single().title)
    }

    @Test
    fun titleAndTagChangedWhileTheFirstInsertRunsAreKeptAndStored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.insertGate = gate
        val persistence = persistence()
        persistence.updateNote { it.copy(title = "dra") }

        val firstSave = async { persistence.save() }
        runCurrent() // the insert is in flight
        persistence.updateNote { it.copy(title = "draft", tagId = TAG_ID) }
        gate.complete(Unit)
        assertTrue(firstSave.await())

        val note = persistence.state.value.note
        assertEquals(INSERTED_ID, note.id)
        assertEquals("draft", note.title)
        assertEquals(TAG_ID, note.tagId)

        // The later changes are not in the database yet, so the next save writes them.
        assertTrue(persistence.save())
        assertEquals("draft", repository.updatedNotes.single().title)
        assertEquals(TAG_ID, repository.updatedNotes.single().tagId)
    }

    @Test
    fun aTagChangeAloneIsAutosaved() = runTest(dispatcher) {
        storeNote(text("body"))
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = it }
        persistence.startAutosave(backgroundScope, contentChangesOf(MutableStateFlow(Unit)))
        passQuietTime()
        assertEquals(0, repository.updateCalls)

        persistence.updateNote { it.copy(tagId = TAG_ID) }
        passQuietTime()

        assertEquals(TAG_ID, repository.updatedNotes.single().tagId)
        assertEquals(BlockConverter.blocksToJson(listOf(text("body"))), repository.updatedNotes.single().value)
    }

    // --- autosave -----------------------------------------------------------------------------

    @Test
    fun autosaveWritesOnceAfterTheQuietTime() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Unit>(replay = 1)
        val persistence = persistence()
        persistence.startAutosave(backgroundScope, changes)
        changes.emit(Unit)

        persistence.updateNote { it.copy(title = "a") }
        runCurrent()
        persistence.updateNote { it.copy(title = "ab") }
        advanceTimeBy(AUTOSAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(0, repository.insertCalls)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, repository.insertCalls)
    }

    @Test
    fun openingANoteAutosavesNothingButTheFirstEditIsSaved() = runTest(dispatcher) {
        storeNote(text("body"))
        val body = MutableStateFlow("body")
        val persistence = persistence(loading = true)
        persistence.load(NOTE_ID, isCopy = false) { blocks = listOf(Block.TextBlock(body)) }
        persistence.startAutosave(backgroundScope, contentChangesOf(body))
        passQuietTime()
        assertEquals(0, repository.updateCalls)

        body.value = "body, edited"
        passQuietTime()

        assertEquals(1, repository.updateCalls)
    }

    @Test
    fun autosaveStopsWhenTheEditorCloses() = runTest(dispatcher) {
        val persistence = persistence()
        persistence.startAutosave(backgroundScope, contentChangesOf(MutableStateFlow(Unit)))
        persistence.updateNote { it.copy(title = "draft") }

        persistence.close()
        passQuietTime()

        assertEquals(0, repository.insertCalls)
    }

    // The autosave runs in backgroundScope, whose timers advanceUntilIdle() does not wait for.
    private fun TestScope.passQuietTime() {
        advanceTimeBy(AUTOSAVE_DEBOUNCE_MS + 1)
        runCurrent()
    }

    private fun contentChangesOf(flow: MutableStateFlow<*>) = flow {
        flow.collect { emit(Unit) }
    }

    private fun persistence(loading: Boolean = false, readOnly: Boolean = false) = NotePersistence(
        notesRepository = repository,
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        ioDispatcher = dispatcher,
        readOnly = readOnly,
        blocks = { blocks },
        copyTitle = { "$it (копія)" },
        loading = loading,
    )

    private fun storeNote(vararg content: Block, isTrash: Boolean = false) {
        val note = Note(
            id = NOTE_ID,
            title = "title",
            value = BlockConverter.blocksToJson(content.toList()),
            isTrash = isTrash,
        )
        repository.allNotesWithTags.value = listOf(NoteWithTag(note = note, tag = null))
    }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private companion object {
        const val NOTE_ID = 91L
        const val INSERTED_ID = 73L
        const val TAG_ID = 5L
        const val OLD_DATE = 1_000L
    }
}
