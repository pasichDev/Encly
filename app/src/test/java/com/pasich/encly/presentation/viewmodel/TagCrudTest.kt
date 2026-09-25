package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.TestTagsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tags: create, rename, hide, delete, select and reorder, including the failure paths. */
@OptIn(ExperimentalCoroutinesApi::class)
class TagCrudTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestTagsRepository(
        listOf(Tag(id = 1, nameTag = "Work", position = 0), Tag(id = 2, nameTag = "Home", position = 1)),
    )
    private val holder = SelectedTagHolder()
    private lateinit var viewModel: TagListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TagListViewModel(repository, ReorderTagsUseCase(repository), holder, TestNotesRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aNewTagGoesFirstWithItsNameTidied() = runTest(dispatcher) {
        advanceUntilIdle()
        var added: Boolean? = null

        viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = "  Side   project ")) { added = it })
        advanceUntilIdle()

        assertEquals(true, added)
        assertEquals(listOf("Side project", "Work", "Home"), names())
    }

    @Test
    fun aBlankNameIsRefusedAtOnce() = runTest(dispatcher) {
        var added: Boolean? = null

        viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = "   ")) { added = it })

        assertEquals(false, added)
        advanceUntilIdle()
        assertEquals(2, repository.tags.value.size)
    }

    @Test
    fun aFailedCreateIsReported() = runTest(dispatcher) {
        advanceUntilIdle()
        val failures = collectFailures()
        repository.failWrites = true
        var added: Boolean? = null

        viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = "Travel")) { added = it })
        advanceUntilIdle()

        assertEquals(false, added)
        assertEquals(listOf(TagOperationFailure.CREATE), failures)
    }

    @Test
    fun renamingKeepsTheSameNameButRefusesAnotherTagsName() = runTest(dispatcher) {
        advanceUntilIdle()
        val failures = collectFailures()
        val results = mutableListOf<Boolean>()

        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 1, nameTag = "WORK")) { results += it })
        advanceUntilIdle()
        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 1, nameTag = "home")) { results += it })
        advanceUntilIdle()
        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 1, nameTag = "")) { results += it })

        assertEquals(listOf(true, false, false), results)
        assertEquals(listOf(TagOperationFailure.NAME_TAKEN), failures)
        assertEquals("WORK", repository.tags.value.single { it.id == 1L }.nameTag)
    }

    @Test
    fun aFailedRenameIsReported() = runTest(dispatcher) {
        advanceUntilIdle()
        val failures = collectFailures()
        repository.failWrites = true

        viewModel.onEvent(TagListEvent.UpdateTag(Tag(id = 2, nameTag = "House")))
        advanceUntilIdle()

        assertEquals(listOf(TagOperationFailure.UPDATE), failures)
    }

    @Test
    fun hidingAndShowingFlipsVisibility() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(TagListEvent.ToggleVisibleTag(repository.tags.value.first()))
        advanceUntilIdle()
        assertFalse(repository.tags.value.first().isVisible)

        viewModel.onEvent(TagListEvent.ToggleVisibleTag(repository.tags.value.first()))
        advanceUntilIdle()
        assertTrue(repository.tags.value.first().isVisible)
    }

    @Test
    fun deletingTheSelectedTagGoesBackToAllNotes() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(TagListEvent.SelectTag(Tag(id = 2, nameTag = "Home")))
        advanceUntilIdle()
        assertEquals(2L, viewModel.state.value.selectedTagId)

        viewModel.onEvent(TagListEvent.DeleteTag(Tag(id = 2, nameTag = "Home")))
        advanceUntilIdle()

        assertEquals(listOf("Work"), names())
        assertEquals(0L, viewModel.state.value.selectedTagId)
        assertEquals(0L, holder.selectedTagFlow.first().id)
    }

    @Test
    fun deletingAnotherTagKeepsTheSelection() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(TagListEvent.SelectTag(Tag(id = 1, nameTag = "Work")))
        advanceUntilIdle()

        viewModel.onEvent(TagListEvent.DeleteTag(Tag(id = 2)))
        advanceUntilIdle()

        assertEquals(1L, viewModel.state.value.selectedTagId)
    }

    @Test
    fun aFailedDeleteIsReported() = runTest(dispatcher) {
        advanceUntilIdle()
        val failures = collectFailures()
        repository.failWrites = true

        viewModel.onEvent(TagListEvent.DeleteTag(Tag(id = 2)))
        advanceUntilIdle()

        assertEquals(listOf(TagOperationFailure.DELETE), failures)
        assertEquals(2, repository.tags.value.size)
    }

    @Test
    fun aDragMovesAtOnceAndSavesAfterAPause() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(TagListEvent.ReorderTagsLive(from = 1, to = 0))
        runCurrent()
        assertEquals(listOf("Home", "Work"), names())
        assertTrue(repository.savedOrders.isEmpty())

        advanceTimeBy(REORDER_PAUSE_MS)
        runCurrent()

        assertEquals(listOf(listOf(0, 1)), repository.savedOrders.map { list -> list.map { it.position } })
        assertEquals(listOf(2L, 1L), repository.savedOrders.single().map { it.id })
    }

    @Test
    fun aFailedReorderPutsTheStoredOrderBack() = runTest(dispatcher) {
        advanceUntilIdle()
        val failures = collectFailures()
        repository.failWrites = true

        viewModel.onEvent(TagListEvent.ReorderTagsLive(from = 1, to = 0))
        runCurrent()
        assertEquals(listOf("Home", "Work"), names())
        advanceUntilIdle()

        assertEquals(listOf(TagOperationFailure.REORDER), failures)
        assertEquals(listOf("Work", "Home"), names())
    }

    @Test
    fun loadingEndsInTheTagsOrAnEmptyList() = runTest(dispatcher) {
        repository.tags.value = emptyList()
        advanceUntilIdle()

        assertEquals(LoadState.Ready(emptyList<Tag>()), viewModel.state.value.tagsLoad)
    }

    private fun names() = (viewModel.state.value.tagsLoad as LoadState.Ready).value.map { it.nameTag }

    private fun TestScope.collectFailures(): List<TagOperationFailure> {
        val failures = mutableListOf<TagOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.operationFailures.collect(failures::add)
        }
        return failures
    }

    private companion object {
        const val REORDER_PAUSE_MS = 301L
    }
}
