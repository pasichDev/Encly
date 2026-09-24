package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeTagsRepository(listOf(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Home")))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val notes = TestNotesRepository()

    private fun viewModel() = TagListViewModel(repository, ReorderTagsUseCase(repository), SelectedTagHolder(), notes)

    @Test
    fun aNewTagNamedLikeAnExistingOneIsRefused() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val failures = mutableListOf<TagOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.operationFailures.collect { failures += it }
        }

        var added: Boolean? = null
        viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = " work ")) { added = it })
        advanceUntilIdle()

        assertEquals(false, added)
        assertEquals(listOf(TagOperationFailure.NAME_TAKEN), failures)
        assertEquals(listOf("Work", "Home"), repository.getTags().first().map { it.nameTag })
    }

    @Test
    fun renamingATagToAnotherTagsNameIsRefusedButItsOwnNameIsFine() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        var renamed: Boolean? = null
        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 2, nameTag = "WORK")) { renamed = it })
        advanceUntilIdle()
        assertEquals(false, renamed)

        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 2, nameTag = "home")) { renamed = it })
        advanceUntilIdle()
        assertEquals(true, renamed)
    }

    @Test
    fun namesAreComparedTrimmedAndIgnoringCase() {
        val tags = listOf(Tag(id = 1, nameTag = "Work"))

        assertTrue(tags.hasTagNamed("  wORk "))
        assertFalse(tags.hasTagNamed("Work", exceptId = 1))
        assertFalse(tags.hasTagNamed("Workout"))
    }

    @Test
    fun aDragMovesFromToAndItsEndPersistsTheOrder() = runTest(dispatcher) {
        repository.reset(listOf(Tag(id = 1, nameTag = "A"), Tag(id = 2, nameTag = "B"), Tag(id = 3, nameTag = "C")))
        val viewModel = viewModel()
        advanceUntilIdle()

        // Two steps of one drag: A moves to the end.
        viewModel.onEvent(TagListEvent.ReorderTagsLive(from = 0, to = 1))
        viewModel.onEvent(TagListEvent.ReorderTagsLive(from = 1, to = 2))
        assertEquals(listOf("B", "C", "A"), viewModel.state.value.listTags.map { it.nameTag })

        viewModel.onEvent(TagListEvent.ReorderTags(viewModel.state.value.listTags))
        advanceUntilIdle()

        assertEquals(listOf(2L to 0, 3L to 1, 1L to 2), repository.savedOrder.last().map { it.id to it.position })
    }

    @Test
    fun aMoveOutsideTheListIsIgnored() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagListEvent.ReorderTagsLive(from = 0, to = 5))
        advanceUntilIdle()

        assertEquals(listOf("Work", "Home"), viewModel.state.value.listTags.map { it.nameTag })
        assertTrue(repository.savedOrder.isEmpty())
    }

    @Test
    fun createAndRenameNormaliseNamesTheSameWay() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = "  Work   stuff ")) {})
        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 2, nameTag = " Home \t office ")))
        advanceUntilIdle()

        val names = repository.getTags().first().map { it.nameTag }
        assertTrue("Work stuff" in names)
        assertTrue("Home office" in names)
    }

    @Test
    fun aBlankNameIsRefused() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        var added: Boolean? = null
        viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = "   ")) { added = it })
        advanceUntilIdle()

        assertEquals(false, added)
        assertEquals(2, repository.getTags().first().size)
    }

    @Test
    fun namesAreCutToTheMaximumLength() {
        assertEquals("a".repeat(TAG_NAME_MAX_LENGTH), normalizeTagName("a".repeat(TAG_NAME_MAX_LENGTH + 5)))
        assertEquals("a b", normalizeTagName(" a \n b "))
    }

    @Test
    fun noteCountsAreByTag() = runTest(dispatcher) {
        notes.allNotesWithTags.value = listOf(
            NoteWithTag(Note(id = 1, tagId = 1), null),
            NoteWithTag(Note(id = 2, tagId = 1), null),
            NoteWithTag(Note(id = 3, tagId = 2), null),
            NoteWithTag(Note(id = 4), null),
        )
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.noteCounts.collect {} }
        advanceUntilIdle()

        assertEquals(mapOf(1L to 2, 2L to 1), viewModel.noteCounts.value)
    }

    private class FakeTagsRepository(initial: List<Tag>) : TagsRepository {
        private val tags = MutableStateFlow(initial)
        private var nextId = initial.maxOf { it.id } + 1

        /** Every list passed to [updateTags] (a saved order), in call order. */
        val savedOrder = mutableListOf<List<Tag>>()

        fun reset(value: List<Tag>) {
            tags.value = value
            nextId = value.maxOf { it.id } + 1
        }

        override fun getTags(): Flow<List<Tag>> = tags

        override suspend fun addTag(tag: Tag): Result<Long> {
            val id = nextId++
            tags.value = tags.value + tag.copy(id = id)
            return Result.success(id)
        }

        override suspend fun deleteTag(tag: Tag): Result<Unit> {
            tags.value = tags.value.filterNot { it.id == tag.id }
            return Result.success(Unit)
        }

        override suspend fun updateTag(tag: Tag): Result<Unit> {
            tags.value = tags.value.map { if (it.id == tag.id) tag else it }
            return Result.success(Unit)
        }

        override suspend fun updateTags(tags: List<Tag>): Result<Unit> {
            savedOrder += tags
            this.tags.value = tags
            return Result.success(Unit)
        }
    }
}
