package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun everyMatchIsReturnedAcrossTagsNotOnlyTheFirstFive() = runTest(dispatcher) {
        val work = Tag(id = 1, nameTag = "Work")
        val personal = Tag(id = 2, nameTag = "Personal")
        repository.allNotesWithTags.value = (1L..8L).map { id ->
            note(id, text = "groceries $id", tag = if (id % 2 == 0L) work else personal)
        } + note(99, text = "unrelated")
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.results.collect {} }

        viewModel.onQueryChange("Groceries")
        advanceUntilIdle()

        assertEquals("Groceries", viewModel.results.value.query)
        assertEquals((1L..8L).toSet(), viewModel.results.value.notes.map { it.note.id }.toSet())
    }

    @Test
    fun notesUnderAHiddenTagAreNotSearched() = runTest(dispatcher) {
        val hidden = Tag(id = 3, nameTag = "Secret", isVisible = false)
        repository.allNotesWithTags.value = listOf(note(1, text = "plan", tag = hidden), note(2, text = "plan"))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.results.collect {} }

        viewModel.onQueryChange("plan")
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.results.value.notes.map { it.note.id })
    }

    @Test
    fun typingIsDebouncedAndMarkersDoNotMatchEveryChecklist() = runTest(dispatcher) {
        repository.allNotesWithTags.value = listOf(
            NoteWithTag(Note(id = 1, title = "list", value = CHECKED_MILK), null),
        )
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.results.collect {} }
        advanceUntilIdle()

        viewModel.onQueryChange("milk")
        advanceTimeBy(DEBOUNCE_MS / 2)
        assertEquals("", viewModel.results.value.query)

        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.results.value.notes.map { it.note.id })

        viewModel.onQueryChange("[x]")
        advanceUntilIdle()
        assertTrue(viewModel.results.value.notes.isEmpty())
    }

    private fun createViewModel() = NoteSearchViewModel(ObserveNotesUseCase(repository, dispatcher), dispatcher)

    private fun note(id: Long, text: String, tag: Tag? = null) = NoteWithTag(
        note = Note(
            id = id,
            title = "note $id",
            value = """[{"blockType":"TEXT","text":"$text"}]""",
            date = id,
            tagId = tag?.id,
        ),
        tag = tag,
    )

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val CHECKED_MILK = """[{"blockType":"LIST_CHECK","items":[{"value":"milk","isCheck":true}]}]"""
    }
}
