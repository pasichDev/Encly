package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
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

    private fun viewModel() = TagListViewModel(repository, ReorderTagsUseCase(repository), SelectedTagHolder())

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

    private class FakeTagsRepository(initial: List<Tag>) : TagsRepository {
        private val tags = MutableStateFlow(initial)
        private var nextId = initial.maxOf { it.id } + 1

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

        override suspend fun updateTags(tags: List<Tag>): Result<Unit> = Result.success(Unit)
    }
}
