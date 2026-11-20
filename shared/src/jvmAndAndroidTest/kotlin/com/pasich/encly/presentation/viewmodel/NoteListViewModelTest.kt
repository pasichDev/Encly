package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteColorUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNotePinStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun staleAllNotesFlowCannotOverwriteSelectedTagResults() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        val sortFlow = MutableStateFlow(NoteSortOption.UPDATED_DESC)
        `when`(settings.getSortNotes).thenReturn(sortFlow)

        val tagSelection = SelectedTagHolder()
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = tagSelection,
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )

        repository.allNotesWithTags.value = listOf(noteWithTitle(ALL_NOTE_ID, "all"))
        advanceUntilIdle()
        assertEquals("all", viewModel.state.value.notes.single().note.title)

        val workTag = Tag(id = WORK_TAG_ID, nameTag = "Work")
        repository.tagFlow(WORK_TAG_ID).value =
            listOf(noteWithTitle(TAGGED_NOTE_ID, "tagged", workTag))
        viewModel.onEvent(NoteListEvent.SelectTag(workTag))
        advanceUntilIdle()

        assertEquals(WORK_TAG_ID, viewModel.state.value.selectedTag)
        assertEquals("tagged", viewModel.state.value.notes.single().note.title)

        repository.allNotesWithTags.value = listOf(noteWithTitle(STALE_NOTE_ID, "stale"))
        advanceUntilIdle()

        assertEquals(WORK_TAG_ID, viewModel.state.value.selectedTag)
        assertEquals("tagged", viewModel.state.value.notes.single().note.title)
    }

    @Test
    fun anEmptyVaultIsReadyAndEmptyNotFailed() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        `when`(settings.getSortNotes).thenReturn(MutableStateFlow(NoteSortOption.UPDATED_DESC))
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = SelectedTagHolder(),
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )
        assertEquals(LoadState.Loading, viewModel.state.value.notesLoad)

        advanceUntilIdle()

        assertEquals(LoadState.Ready(emptyList<Any>()), viewModel.state.value.notesLoad)
    }

    @Test
    fun aFailedReadIsShownAsAnErrorNotAsAnEmptyVault() = runTest {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getAllNotesWithTag(): Flow<List<NoteWithTags>> = flow { throw IOException("vault closed") }
        }
        val settings = mock(SettingsRepository::class.java)
        `when`(settings.getSortNotes).thenReturn(MutableStateFlow(NoteSortOption.UPDATED_DESC))
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(failing, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(failing),
            updateNoteTagUseCase = UpdateNoteTagUseCase(failing),
            selectedTagHolder = SelectedTagHolder(),
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(failing),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(failing),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(failing),
            updateNoteColorUseCase = UpdateNoteColorUseCase(failing),
        )

        advanceUntilIdle()

        val load = viewModel.state.value.notesLoad
        assertTrue(load is LoadState.Failed)
        assertEquals(ListLoadErrors.NOTES, (load as LoadState.Failed).error)
        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    @Test
    fun aNewSortOrderOrANewNoteAsksTheListToScrollToTheTop() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        val sortFlow = MutableStateFlow(NoteSortOption.UPDATED_DESC)
        `when`(settings.getSortNotes).thenReturn(sortFlow)
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = SelectedTagHolder(),
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )
        repository.allNotesWithTags.value = listOf(noteWithTitle(1, "a"), noteWithTitle(2, "b"))
        advanceUntilIdle()
        // The first load keeps the position the list restores, also after process death.
        assertNull(viewModel.scrollToTopRequest.value)

        sortFlow.value = NoteSortOption.CREATED_ASC
        advanceUntilIdle()
        val afterSort = viewModel.scrollToTopRequest.value
        assertNotNull(afterSort)
        viewModel.onScrolledToTop(afterSort!!)
        assertNull(viewModel.scrollToTopRequest.value)

        // A new note while the editor is open stays pending until the list has scrolled.
        repository.allNotesWithTags.value = repository.allNotesWithTags.value + noteWithTitle(3, "new")
        advanceUntilIdle()
        val afterNewNote = viewModel.scrollToTopRequest.value
        assertNotNull(afterNewNote)

        // An edit or a note leaving the list keeps the position.
        repository.allNotesWithTags.value = listOf(noteWithTitle(1, "a edited"), noteWithTitle(3, "new"))
        advanceUntilIdle()
        assertEquals(afterNewNote, viewModel.scrollToTopRequest.value)
        viewModel.onScrolledToTop(afterNewNote!!)
        assertNull(viewModel.scrollToTopRequest.value)
    }

    @Test
    fun aScrollReportedForAnOlderRequestKeepsTheNewerOnePending() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        val sortFlow = MutableStateFlow(NoteSortOption.UPDATED_DESC)
        `when`(settings.getSortNotes).thenReturn(sortFlow)
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = SelectedTagHolder(),
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )
        repository.allNotesWithTags.value = listOf(noteWithTitle(1, "a"))
        advanceUntilIdle()

        sortFlow.value = NoteSortOption.CREATED_ASC
        advanceUntilIdle()
        val first = viewModel.scrollToTopRequest.value!!
        repository.allNotesWithTags.value = repository.allNotesWithTags.value + noteWithTitle(2, "new")
        advanceUntilIdle()
        val second = viewModel.scrollToTopRequest.value!!

        viewModel.onScrolledToTop(first)
        assertEquals(second, viewModel.scrollToTopRequest.value)
    }

    @Test
    fun rapidTagAndSortSwitchesEndOnTheLastSelectionWithoutAStaleOrDoubledList() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        val sortFlow = MutableStateFlow(NoteSortOption.UPDATED_DESC)
        `when`(settings.getSortNotes).thenReturn(sortFlow)
        val tagSelection = SelectedTagHolder()
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = tagSelection,
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )
        val work = Tag(id = WORK_TAG_ID, nameTag = "Work")
        val home = Tag(id = HOME_TAG_ID, nameTag = "Home")
        repository.allNotesWithTags.value = listOf(noteWithTitle(1, "all-1"), noteWithTitle(2, "all-2"))
        repository.tagFlow(WORK_TAG_ID).value =
            listOf(noteWithTitle(3, "work-3", work), noteWithTitle(4, "work-4", work))
        repository.tagFlow(HOME_TAG_ID).value =
            listOf(noteWithTitle(5, "home-5", home), noteWithTitle(6, "home-6", home))
        advanceUntilIdle()

        // Every list the screen is ever handed: none may repeat a note.
        val shown = mutableListOf<List<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { state -> shown += state.notes.map { it.note.id } }
        }

        // Several switches in a row, none awaited: only the last tag and the last sort may remain.
        viewModel.onEvent(NoteListEvent.SelectTag(work))
        sortFlow.value = NoteSortOption.CREATED_ASC
        viewModel.onEvent(NoteListEvent.SelectTag(home))
        viewModel.onEvent(NoteListEvent.SelectTag(Tag()))
        sortFlow.value = NoteSortOption.UPDATED_DESC
        viewModel.onEvent(NoteListEvent.SelectTag(work))
        sortFlow.value = NoteSortOption.CREATED_ASC
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(WORK_TAG_ID, state.selectedTag)
        assertEquals(NoteSortOption.CREATED_ASC, state.noteSortOption)
        assertEquals(listOf("work-3", "work-4"), state.notes.map { it.note.title })
        shown.forEach { ids -> assertEquals("a list repeats a note: $ids", ids.distinct(), ids) }

        // A late change of a list that is no longer selected does not reach the screen.
        repository.allNotesWithTags.value = listOf(noteWithTitle(7, "all-late"))
        repository.tagFlow(HOME_TAG_ID).value = listOf(noteWithTitle(8, "home-late", home))
        advanceUntilIdle()
        assertEquals(listOf("work-3", "work-4"), viewModel.state.value.notes.map { it.note.title })
    }

    private fun noteWithTitle(id: Long, title: String, tag: Tag? = null): NoteWithTags = NoteWithTags(
        Note(
            id = id,
            title = title,
            date = id,
            dateCreate = id,
            tagIds = setOfNotNull(tag?.id),
        ),
        listOfNotNull(tag),
    )

    private companion object {
        const val WORK_TAG_ID = 7L
        const val HOME_TAG_ID = 8L
        const val ALL_NOTE_ID = 11L
        const val TAGGED_NOTE_ID = 12L
        const val STALE_NOTE_ID = 13L
    }
}
