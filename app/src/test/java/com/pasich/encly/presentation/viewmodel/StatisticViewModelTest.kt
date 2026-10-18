package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.testutil.TestTagsRepository
import com.pasich.encly.testutil.TestTasksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The drawer's counts: a read that fails once the vault closed keeps the last count. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val tags = TestTagsRepository(listOf(Tag(id = 1, nameTag = "a"), Tag(id = 2, nameTag = "b")))
    private val tasks = TestTasksRepository(listOf(Task(id = 1, title = "Open")))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aFailedReadKeepsTheLastCountsInsteadOfCrashing() = runTest(dispatcher) {
        val viewModel = StatisticViewModel(tags, tasks)
        backgroundScope.launch { viewModel.totalTagsCreated.collect {} }
        backgroundScope.launch { viewModel.openTasksCount.collect {} }
        advanceUntilIdle()
        assertEquals(2, viewModel.totalTagsCreated.value)
        assertEquals(1, viewModel.openTasksCount.value)

        tags.failReads = true
        tasks.failReads = true
        tags.tags.value = tags.tags.value + Tag(id = 3, nameTag = "c")
        tasks.tasks.value = tasks.tasks.value + Task(id = 2, title = "Another")
        advanceUntilIdle()

        assertEquals(2, viewModel.totalTagsCreated.value)
        assertEquals(1, viewModel.openTasksCount.value)
    }
}
