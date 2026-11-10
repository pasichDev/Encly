package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteColorUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNotePinStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.TestSettingsRepository
import com.pasich.encly.testutil.TestTagsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a lock leaves behind in the screens that hold the vault's content, and in the tag selection. */
@OptIn(ExperimentalCoroutinesApi::class)
class LockClearsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val locked = MutableStateFlow(false)
    private val lockEvents = object : VaultLockEvents {
        override val locked = this@LockClearsViewModelsTest.locked
    }
    private val repository = TestNotesRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun noteList(holder: SelectedTagHolder) = NoteListViewModel(
        observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
        updateNotePinStatusUseCase = UpdateNotePinStatusUseCase(repository),
        updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
        updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        selectedTagHolder = holder,
        settingsRepository = TestSettingsRepository(),
        updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
        lockEvents = lockEvents,
    )

    // --- the tag selection -------------------------------------------------------------------------

    @Test
    fun theSelectedTagDoesNotSurviveALock() = runTest(dispatcher) {
        val holder = SelectedTagHolder(lockEvents)
        holder.selectTag(Tag(id = REAL_TAG, nameTag = "Lawyer"))

        locked.value = true
        locked.value = false

        assertEquals(Tag(), holder.selectedTagFlow.first())
    }

    @Test
    fun aDecoyVaultOpenedAfterTheRealOneIsNotFilteredByItsTag() = runTest(dispatcher) {
        val holder = SelectedTagHolder(lockEvents)
        val real = noteList(holder)
        advanceUntilIdle()
        real.onEvent(NoteListEvent.SelectTag(Tag(id = REAL_TAG, nameTag = "Lawyer")))
        advanceUntilIdle()
        assertEquals(REAL_TAG, real.state.value.selectedTag)

        // Lock, then the duress PIN opens the decoy vault with notes of its own.
        locked.value = true
        advanceUntilIdle()
        locked.value = false
        repository.allNotesWithTags.value = listOf(NoteWithTags(Note(id = 1, title = "Shopping"), emptyList()))
        val decoy = noteList(holder)
        advanceUntilIdle()

        assertEquals(0L, decoy.state.value.selectedTag)
        assertEquals("", decoy.state.value.selectedTagName)
        assertEquals(listOf("Shopping"), decoy.state.value.notes.map { it.note.title })
        assertEquals(0L, decoy.state.value.selectedTag) // so a new note there starts untagged
    }

    @Test
    fun aSelectionThatIsNoTagOfTheOpenVaultIsDropped() = runTest(dispatcher) {
        val holder = SelectedTagHolder()
        val tags = TagListViewModel(
            TestTagsRepository(listOf(Tag(id = 1, nameTag = "Work"))),
            ReorderTagsUseCase(TestTagsRepository()),
            holder,
            repository,
        )
        holder.selectTag(Tag(id = REAL_TAG, nameTag = "Lawyer"))
        advanceUntilIdle()

        assertEquals(0L, holder.selectedTagFlow.first().id)
        assertEquals(0L, tags.state.value.selectedTagId)
    }

    // --- the screens that hold content --------------------------------------------------------------

    @Test
    fun theTrashIsEmptiedByALock() = runTest(dispatcher) {
        repository.trashNotes.value = listOf(Note(id = 1, title = "secret", isTrash = true))
        val viewModel = TrashViewModel(
            repository,
            UpdateNoteTrashStatusUseCase(repository),
            CleanTrashNotesUseCase(repository),
            lockEvents,
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.notes.size)

        locked.value = true
        advanceUntilIdle()

        assertEquals(LoadState.Loading, viewModel.state.value.notesLoad)
        assertTrue(viewModel.state.value.notes.isEmpty())
        // What the closed vault might still emit does not come back.
        repository.trashNotes.value = listOf(Note(id = 2, title = "later", isTrash = true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    @Test
    fun theSearchQueryAndResultsAreClearedByALock() = runTest(dispatcher) {
        repository.allNotesWithTags.value = listOf(
            NoteWithTags(
                Note(id = 1, title = "salary", value = """[{"blockType":"TEXT","text":"salary 100"}]"""),
                emptyList(),
            ),
        )
        val viewModel = NoteSearchViewModel(ObserveNotesUseCase(repository, dispatcher), dispatcher, lockEvents)
        viewModel.onQueryChange("salary")
        advanceUntilIdle()
        assertEquals(1, viewModel.results.value.notes.size)

        locked.value = true
        advanceUntilIdle()

        assertEquals("", viewModel.query.value)
        assertEquals(NoteSearchViewModel.SearchResults(), viewModel.results.value)
    }

    @Test
    fun theTagNamesAreClearedByALock() = runTest(dispatcher) {
        val tagsRepository = TestTagsRepository(listOf(Tag(id = 1, nameTag = "Lawyer", isVisible = false)))
        val viewModel = TagListViewModel(
            tagsRepository,
            ReorderTagsUseCase(tagsRepository),
            SelectedTagHolder(),
            repository,
            lockEvents,
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.listTags.size)

        locked.value = true
        advanceUntilIdle()

        assertTrue(viewModel.state.value.listTags.isEmpty())
        assertEquals(LoadState.Loading, viewModel.state.value.tagsLoad)
    }

    private companion object {
        const val REAL_TAG = 5L
    }
}
