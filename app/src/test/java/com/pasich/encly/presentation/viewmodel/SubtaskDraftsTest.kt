package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Subtask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskDraftsTest {
    private val drafts = listOf("a", "b", "c", "d").mapIndexed { i, title ->
        SubtaskDraft(key = i.toLong(), title = title)
    }

    @Test
    fun moveDownAndUp() {
        assertEquals(listOf("b", "c", "a", "d"), SubtaskDrafts.move(drafts, 0, 2).map { it.title })
        assertEquals(listOf("d", "a", "b", "c"), SubtaskDrafts.move(drafts, 3, 0).map { it.title })
        assertEquals(listOf("a", "c", "b", "d"), SubtaskDrafts.move(drafts, 1, 2).map { it.title })
    }

    @Test
    fun moveOutOfRangeOrInPlaceChangesNothing() {
        assertSame(drafts, SubtaskDrafts.move(drafts, 1, 1))
        assertSame(drafts, SubtaskDrafts.move(drafts, -1, 2))
        assertSame(drafts, SubtaskDrafts.move(drafts, 0, 4))
    }

    @Test
    fun savedRowsFollowTheEditorOrderAndDropBlankOnes() {
        val edited = listOf(
            SubtaskDraft(key = 7, title = " Milk ", isCompleted = true, id = 7, uid = "u7"),
            SubtaskDraft(key = -1, title = "   "),
            SubtaskDraft(key = -2, title = "Eggs"),
        )

        assertEquals(
            listOf(
                Subtask(id = 7, taskId = 3, title = "Milk", isCompleted = true, position = 0, uid = "u7"),
                Subtask(id = 0, taskId = 3, title = "Eggs", isCompleted = false, position = 1, uid = ""),
            ),
            SubtaskDrafts.toSubtasks(3, edited),
        )
    }

    @Test
    fun loadedRowsKeepTheirIdentity() {
        val row = Subtask(id = 4, taskId = 1, title = "Milk", isCompleted = true, position = 0, uid = "u4")

        assertEquals(
            listOf(SubtaskDraft(key = 4, title = "Milk", isCompleted = true, id = 4, uid = "u4")),
            SubtaskDrafts.fromSubtasks(listOf(row)),
        )
    }

    @Test
    fun completionIsOfferedOnlyWhenTheLastOpenSubtaskIsTicked() {
        val open = listOf(sub(done = true), sub(done = false))
        val allDone = listOf(sub(done = true), sub(done = true))

        assertTrue(SubtaskDrafts.offersCompletion(taskCompleted = false, before = open, after = allDone))
        // Already all done before: nothing new to offer.
        assertFalse(SubtaskDrafts.offersCompletion(taskCompleted = false, before = allDone, after = allDone))
        // The task is already completed.
        assertFalse(SubtaskDrafts.offersCompletion(taskCompleted = true, before = open, after = allDone))
        // Something still open, or no checklist at all.
        assertFalse(SubtaskDrafts.offersCompletion(taskCompleted = false, before = open, after = open))
        assertFalse(SubtaskDrafts.offersCompletion(taskCompleted = false, before = open, after = emptyList()))
    }

    private fun sub(done: Boolean) = Subtask(taskId = 1, title = "x", isCompleted = done)
}
