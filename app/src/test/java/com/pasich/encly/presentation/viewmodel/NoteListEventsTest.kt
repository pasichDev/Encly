package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteColorUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNotePinStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.TestSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The notes list's actions (trash, tag, description, sort, filter) and its scroll-to-top. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteListEventsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository()
    private val settings = TestSettingsRepository()
    private val tags = SelectedTagHolder()
    private lateinit var viewModel: NoteListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.allNotesWithTags.value = listOf(
            entry(Note(id = 1, title = "Older", date = 10, dateCreate = 30)),
            entry(Note(id = 2, title = "Newer", date = 20, dateCreate = 10)),
            entry(
                Note(id = 3, title = "Hidden", date = 30, tagId = HIDDEN_TAG),
                Tag(id = HIDDEN_TAG, isVisible = false),
            ),
        )
        viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = tags,
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun allNotesAreSortedByTheStoredOrderWithoutHiddenTags() = runTest(dispatcher) {
        advanceUntilIdle()

        assertEquals(listOf("Newer", "Older"), titles())
        assertEquals(NoteSortOption.UPDATED_DESC, viewModel.state.value.noteSortOption)
    }

    @Test
    fun aNewSortIsStoredAppliedAndScrollsToTheTop() = runTest(dispatcher) {
        advanceUntilIdle()
        assertNull("the first load keeps the restored position", viewModel.scrollToTopRequest.value)

        viewModel.onEvent(NoteListEvent.ToggleNoteSort(NoteSortOption.CREATED_DESC))
        advanceUntilIdle()

        assertEquals(NoteSortOption.CREATED_DESC, settings.getSortNotes.value)
        assertEquals(listOf("Older", "Newer"), titles())
        val request = viewModel.scrollToTopRequest.value
        assertTrue(request != null)

        viewModel.onScrolledToTop(request!!)
        assertNull(viewModel.scrollToTopRequest.value)
    }

    @Test
    fun aNoteThatAppearsScrollsToTheTopButAnEditDoesNot() = runTest(dispatcher) {
        advanceUntilIdle()

        repository.allNotesWithTags.value = repository.allNotesWithTags.value.map {
            if (it.note.id == 1L) entry(it.note.copy(title = "Older, edited")) else it
        }
        advanceUntilIdle()
        assertNull(viewModel.scrollToTopRequest.value)

        repository.allNotesWithTags.value =
            repository.allNotesWithTags.value + entry(Note(id = 4, title = "New", date = 40))
        advanceUntilIdle()
        assertTrue(viewModel.scrollToTopRequest.value != null)
    }

    @Test
    fun anOlderScrollRequestDoesNotClearANewerOne() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(NoteListEvent.ToggleNoteSort(NoteSortOption.CREATED_DESC))
        advanceUntilIdle()
        val first = viewModel.scrollToTopRequest.value!!
        viewModel.onEvent(NoteListEvent.ToggleNoteSort(NoteSortOption.UPDATED_ASC))
        advanceUntilIdle()

        viewModel.onScrolledToTop(first)

        assertEquals(first + 1, viewModel.scrollToTopRequest.value)
    }

    @Test
    fun anActionOnAStaleNoteWritesOnlyItsOwnColumn() = runTest(dispatcher) {
        advanceUntilIdle()
        // The sheet still holds the note as read in tag 7's view; its tag was changed to 4 since.
        val stale = note(1).copy(tagId = 7)

        viewModel.onEvent(NoteListEvent.ChangeTag(stale, idTag = WORK_TAG))
        viewModel.onEvent(NoteListEvent.ChangeColor(stale, NoteColor.TEAL))
        viewModel.onEvent(NoteListEvent.PinNote(stale, pinned = true))
        viewModel.onEvent(NoteListEvent.ArchiveNote(stale, archived = true))
        advanceUntilIdle()

        // No write carries the stale tag (or any other column) back.
        assertEquals(
            listOf("tag:1=$WORK_TAG", "color:1=teal", "pinned:1=true", "archived:1=true"),
            repository.columnWrites,
        )
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun performTellsWhetherTheWriteSucceeded() = runTest(dispatcher) {
        advanceUntilIdle()

        assertTrue(viewModel.perform(NoteListEvent.PinNote(note(1), true)))

        repository.columnWriteResult = false
        assertFalse(viewModel.perform(NoteListEvent.PinNote(note(1), true)))
        assertFalse(viewModel.perform(NoteListEvent.ArchiveNote(note(1), true)))
    }

    @Test
    fun movingANoteToTheTrashStoresItAsTrashed() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(NoteListEvent.NoteToTrash(note(1)))
        advanceUntilIdle()

        assertTrue(repository.updatedNotes.single().isTrash)
        assertEquals(1L, repository.updatedNotes.single().id)
    }

    @Test
    fun changingTheTagAndTheDescriptionStoresThem() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(NoteListEvent.ChangeTag(note(1), idTag = WORK_TAG))
        viewModel.onEvent(NoteListEvent.ChangeDescription(note(2), "Short summary"))
        advanceUntilIdle()

        assertEquals(listOf("tag:1=$WORK_TAG", "description:2=Short summary"), repository.columnWrites)
    }

    @Test
    fun selectingAHiddenTagShowsItsNotesAndItsName() = runTest(dispatcher) {
        advanceUntilIdle()
        repository.tagFlow(HIDDEN_TAG).value = listOf(repository.allNotesWithTags.value.last())

        viewModel.onEvent(NoteListEvent.SelectTag(Tag(id = HIDDEN_TAG, nameTag = "Private", isVisible = false)))
        advanceUntilIdle()

        assertEquals(HIDDEN_TAG, viewModel.state.value.selectedTag)
        assertEquals("Private", viewModel.state.value.selectedTagName)
        assertEquals(listOf("Hidden"), titles())
    }

    private fun titles() = viewModel.state.value.notes.map { it.note.title }

    private fun note(id: Long) = repository.allNotesWithTags.value.single { it.note.id == id }.note

    private fun entry(note: Note, tag: Tag? = null) = NoteWithTag(note = note, tag = tag)

    private companion object {
        const val HIDDEN_TAG = 9L
        const val WORK_TAG = 4L
    }
}
