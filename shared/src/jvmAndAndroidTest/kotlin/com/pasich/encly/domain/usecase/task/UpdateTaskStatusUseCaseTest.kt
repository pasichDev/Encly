package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.model.Task
import com.pasich.encly.testutil.TestTasksRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTaskStatusUseCaseTest {
    private val repository = TestTasksRepository(listOf(Task(id = 1, title = "t")))
    private val useCase = UpdateTaskStatusUseCase(repository)

    @Test
    fun completingRecordsTheTime() = runTest {
        val before = System.currentTimeMillis()

        assertTrue(useCase(1, isCompleted = true).isSuccess)

        val date = repository.statusUpdates.single().third
        assertTrue(date != null && date >= before && date <= System.currentTimeMillis())
        assertTrue(repository.tasks.value.single().isCompleted)
    }

    @Test
    fun reopeningClearsTheTime() = runTest {
        useCase(1, isCompleted = true)

        useCase(1, isCompleted = false)

        assertNull(repository.statusUpdates.last().third)
        assertNull(repository.tasks.value.single().completedDate)
    }

    @Test
    fun anExplicitTimeIsKept() = runTest {
        useCase(1, isCompleted = true, completedDate = COMPLETED_AT)

        assertEquals(COMPLETED_AT, repository.tasks.value.single().completedDate)
    }

    @Test
    fun aFailedWriteIsReturned() = runTest {
        repository.failStatus = true

        assertTrue(useCase(1, isCompleted = true).isFailure)
    }

    private companion object {
        const val COMPLETED_AT = 1_700_000_000_000L
    }
}
