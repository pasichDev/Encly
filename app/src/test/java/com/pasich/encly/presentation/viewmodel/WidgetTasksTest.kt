package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

/** The two tasks the notes screen previews (design spec §4.3). */
class WidgetTasksTest {

    private fun task(id: Long, priority: Int, done: Boolean = false) =
        Task(id = id, title = "Task $id", priority = priority, isCompleted = done)

    @Test
    fun highestPriorityFirstAtMostTwo() {
        val tasks = listOf(task(1, 0), task(2, 2), task(3, 1), task(4, 2))

        assertEquals(listOf(2L, 4L), widgetTasks(tasks).map { it.id })
    }

    @Test
    fun equalPrioritiesKeepTheirOrder() {
        val tasks = listOf(task(1, 1), task(2, 1), task(3, 1))

        assertEquals(listOf(1L, 2L), widgetTasks(tasks).map { it.id })
    }

    @Test
    fun completedTasksAreNeverPreviewed() {
        val tasks = listOf(task(1, 2, done = true), task(2, 0))

        assertEquals(listOf(2L), widgetTasks(tasks).map { it.id })
    }

    @Test
    fun noOpenTasksMeansNoPreview() {
        assertEquals(emptyList<Task>(), widgetTasks(listOf(task(1, 2, done = true))))
    }
}
