package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The trash and tag lists show a failed read as an error, and an empty one as empty. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListLoadStateViewModelTest {
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
    fun aFailedTrashReadIsAnErrorNotAnEmptyTrash() = runTest {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getTrashNotes(): Flow<List<Note>> = flow { throw IOException("vault closed") }
        }

        val viewModel = trashViewModel(failing)
        advanceUntilIdle()

        assertEquals(LoadState.Failed(ListLoadErrors.NOTES), viewModel.state.value.notesLoad)
    }

    @Test
    fun anEmptyTrashIsReady() = runTest {
        val viewModel = trashViewModel(TestNotesRepository())
        assertEquals(LoadState.Loading, viewModel.state.value.notesLoad)

        advanceUntilIdle()

        assertEquals(LoadState.Ready(emptyList<Note>()), viewModel.state.value.notesLoad)
    }

    @Test
    fun selectingInAFailedTrashKeepsTheError() = runTest {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getTrashNotes(): Flow<List<Note>> = flow { throw IOException("vault closed") }
        }
        val viewModel = trashViewModel(failing)
        advanceUntilIdle()

        viewModel.onEvent(TrashListEvent.RestoreNotes())

        assertTrue(viewModel.state.value.notesLoad is LoadState.Failed)
    }

    @Test
    fun aFailedTagReadIsAnErrorNotAnEmptyTagList() = runTest {
        val viewModel = tagListViewModel(FakeTagsRepository(fail = true))
        advanceUntilIdle()

        assertEquals(LoadState.Failed(ListLoadErrors.TAGS), viewModel.state.value.tagsLoad)
        assertTrue(viewModel.state.value.listTags.isEmpty())
    }

    @Test
    fun tagsLoadInTheirOrder() = runTest {
        val tags = listOf(Tag(id = 1, nameTag = "a"), Tag(id = 2, nameTag = "b"))
        val viewModel = tagListViewModel(FakeTagsRepository(tags = tags))
        advanceUntilIdle()

        assertEquals(tags, viewModel.state.value.listTags)
    }

    private fun trashViewModel(repository: NotesRepository) = TrashViewModel(
        notesRepository = repository,
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        cleanTrashNotesUseCase = CleanTrashNotesUseCase(repository),
    )

    private fun tagListViewModel(repository: TagsRepository) = TagListViewModel(
        tagsRepository = repository,
        reorderTagsUseCase = ReorderTagsUseCase(repository),
        selectedTagHolder = SelectedTagHolder(),
    )

    private class FakeTagsRepository(private val fail: Boolean = false, tags: List<Tag> = emptyList()) :
        TagsRepository {
        private val stored = MutableStateFlow(tags)

        override fun getTags(): Flow<List<Tag>> = if (fail) flow { throw IOException("vault closed") } else stored

        override suspend fun addTag(tag: Tag): Result<Long> = Result.success(tag.id)
        override suspend fun deleteTag(tag: Tag): Result<Unit> = Result.success(Unit)
        override suspend fun updateTag(tag: Tag): Result<Unit> = Result.success(Unit)
        override suspend fun updateTags(tags: List<Tag>): Result<Unit> = Result.success(Unit)
    }
}
