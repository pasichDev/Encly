package com.pasich.encly.data.repository

import com.pasich.encly.data.database.AppDatabase
import com.pasich.encly.data.database.DatabaseLease
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The checklist half of the task repository: reads pass through, writes fail when no row changed. */
class TasksRepositorySubtasksTest {
    private val dao = mock(TasksDao::class.java)
    private val database = mock(AppDatabase::class.java).also { `when`(it.tasksDao()).thenReturn(dao) }
    private val lease = DatabaseLease()
    private val provider = object : DatabaseProvider {
        override fun captureLease(): DatabaseLease = lease
        override fun getDatabase(lease: DatabaseLease): AppDatabase = database
    }
    private val repository = TasksRepositoryImpl(provider)

    @Test
    fun subtasksAreObservedAndReadFromTheDao() = runBlocking {
        val subtasks = listOf(Subtask(id = 1, taskId = 7, title = "a"), Subtask(id = 2, taskId = 7, title = "b"))
        `when`(dao.observeSubtasks()).thenReturn(flowOf(subtasks))
        `when`(dao.getSubtasks(7)).thenReturn(subtasks)

        assertEquals(subtasks, repository.observeSubtasks().first())
        assertEquals(subtasks, repository.getSubtasks(7).getOrNull())
    }

    @Test
    fun addingASubtaskReturnsTheNewId() = runBlocking {
        `when`(dao.appendSubtask(7, "new")).thenReturn(42L)

        assertEquals(42L, repository.addSubtask(7, "new").getOrNull())
    }

    @Test
    fun changingASubtaskThatIsGoneFails() = runBlocking {
        `when`(dao.setSubtaskCompleted(1, true)).thenReturn(1)
        `when`(dao.setSubtaskCompleted(2, true)).thenReturn(0)
        `when`(dao.renameSubtask(1, "x")).thenReturn(1)
        `when`(dao.renameSubtask(2, "x")).thenReturn(0)
        `when`(dao.deleteSubtaskById(1)).thenReturn(1)
        `when`(dao.deleteSubtaskById(2)).thenReturn(0)

        assertTrue(repository.setSubtaskCompleted(1, true).isSuccess)
        assertTrue(repository.setSubtaskCompleted(2, true).isFailure)
        assertTrue(repository.renameSubtask(1, "x").isSuccess)
        assertTrue(repository.renameSubtask(2, "x").isFailure)
        assertTrue(repository.deleteSubtask(1).isSuccess)
        assertTrue(repository.deleteSubtask(2).isFailure)
    }

    @Test
    fun restoringAndReplacingGoThroughTheDaoInOneCall() = runBlocking {
        val task = Task(id = 7, title = "t")
        val subtasks = listOf(Subtask(id = 1, taskId = 7, title = "a"))

        assertTrue(repository.restoreSubtask(subtasks.single()).isSuccess)
        assertTrue(repository.saveSubtasks(7, subtasks).isSuccess)
        assertTrue(repository.restoreTask(task, subtasks).isSuccess)

        verify(dao).restoreSubtask(subtasks.single())
        verify(dao).replaceSubtasks(7, subtasks)
        verify(dao).restoreTask(task, subtasks)
    }

    @Test
    fun aStorageErrorBecomesAFailureInsteadOfThrowing() = runBlocking {
        `when`(dao.getSubtasks(7)).thenThrow(IllegalStateException("database closed"))
        `when`(dao.appendSubtask(7, "x")).thenThrow(IllegalStateException("database closed"))

        assertTrue(repository.getSubtasks(7).isFailure)
        assertTrue(repository.addSubtask(7, "x").isFailure)
    }
}
