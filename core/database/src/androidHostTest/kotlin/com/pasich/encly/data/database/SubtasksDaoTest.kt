package com.pasich.encly.data.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sub-tasks in the real Room schema (plain SQLite under Robolectric, not SQLCipher): the
 * cascade on delete, the uid trigger, and saving an edited checklist in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubtasksDaoTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.tasksDao()

    @Before
    fun setUp() {
        db = VaultSchema.install(
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries(),
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingATaskDeletesItsSubtasksOnly() = runBlocking {
        val shop = dao.insertTask(Task(title = "Shop"))
        val call = dao.insertTask(Task(title = "Call"))
        dao.replaceSubtasks(
            shop,
            listOf(Subtask(taskId = shop, title = "Milk"), Subtask(taskId = shop, title = "Eggs")),
        )
        dao.replaceSubtasks(call, listOf(Subtask(taskId = call, title = "Mom")))

        dao.deleteTaskById(shop)

        assertTrue(dao.getSubtasks(shop).isEmpty())
        assertEquals(listOf("Mom"), dao.getSubtasks(call).map { it.title })
        assertEquals(listOf("Mom"), db.backupDao().allSubtasks().map { it.title })
    }

    @Test
    fun clearingCompletedTasksDeletesTheirSubtasks() = runBlocking {
        val done = dao.insertTask(Task(title = "Done", isCompleted = true))
        dao.replaceSubtasks(done, listOf(Subtask(taskId = done, title = "Step")))

        dao.deleteAllCompletedTasks()

        assertTrue(db.backupDao().allSubtasks().isEmpty())
    }

    @Test
    fun aNewSubtaskGetsAUidAndKeepsItWhenEdited() = runBlocking {
        val task = dao.insertTask(Task(title = "Shop"))
        dao.replaceSubtasks(task, listOf(Subtask(taskId = task, title = "Milk")))
        val saved = dao.getSubtasks(task).single()
        assertEquals(32, saved.uid.length)

        // The editor round-trips rows it loaded; a row built without its uid keeps the stored one.
        dao.replaceSubtasks(task, listOf(saved.copy(title = "Oat milk", isCompleted = true, uid = "")))

        val edited = dao.getSubtasks(task).single()
        assertEquals(saved.id, edited.id)
        assertEquals(saved.uid, edited.uid)
        assertEquals("Oat milk", edited.title)
        assertTrue(edited.isCompleted)
    }

    @Test
    fun savingAChecklistReordersUpdatesAddsAndRemovesInOneGo() = runBlocking {
        val task = dao.insertTask(Task(title = "Trip"))
        dao.replaceSubtasks(
            task,
            listOf("Tickets", "Hotel", "Bags").map { Subtask(taskId = task, title = it) },
        )
        val (tickets, hotel, bags) = dao.getSubtasks(task)

        dao.replaceSubtasks(task, listOf(bags, Subtask(taskId = task, title = "Passport"), tickets))

        val result = dao.getSubtasks(task)
        assertEquals(listOf("Bags", "Passport", "Tickets"), result.map { it.title })
        assertEquals(listOf(0, 1, 2), result.map { it.position })
        assertEquals(listOf(bags.uid, tickets.uid), listOf(result[0].uid, result[2].uid))
        assertTrue(result.none { it.id == hotel.id })
    }

    @Test
    fun observeSubtasksListsEveryTaskInItsOrder() = runBlocking {
        val trip = dao.insertTask(Task(title = "Trip"))
        val shop = dao.insertTask(Task(title = "Shop"))
        dao.insertTask(Task(title = "No checklist"))
        dao.replaceSubtasks(shop, listOf(Subtask(taskId = shop, title = "Milk")))
        dao.replaceSubtasks(
            trip,
            listOf(
                Subtask(taskId = trip, title = "a", isCompleted = true),
                Subtask(taskId = trip, title = "b"),
            ),
        )

        val rows = dao.observeSubtasks().first()

        assertEquals(listOf(trip to "a", trip to "b", shop to "Milk"), rows.map { it.taskId to it.title })
        assertEquals(listOf(0, 1, 0), rows.map { it.position })
    }

    @Test
    fun setSubtaskCompletedTicksAndUnticksOneRow() = runBlocking {
        val task = dao.insertTask(Task(title = "Trip"))
        dao.replaceSubtasks(task, listOf(Subtask(taskId = task, title = "a"), Subtask(taskId = task, title = "b")))
        val (a, b) = dao.getSubtasks(task)

        assertEquals(1, dao.setSubtaskCompleted(a.id, done = true))
        assertEquals(listOf(true, false), dao.observeSubtasks().first().map { it.isCompleted })

        dao.setSubtaskCompleted(a.id, done = false)
        assertEquals(listOf(false, false), dao.getSubtasks(task).map { it.isCompleted })
        assertEquals(b.uid, dao.getSubtasks(task)[1].uid)
        assertEquals(0, dao.setSubtaskCompleted(id = 999, done = true))
    }

    @Test
    fun appendSubtaskAddsAfterTheLastOne() = runBlocking {
        val task = dao.insertTask(Task(title = "Trip"))
        val first = dao.appendSubtask(task, "Tickets")
        dao.replaceSubtasks(task, dao.getSubtasks(task) + Subtask(taskId = task, title = "Hotel"))

        val last = dao.appendSubtask(task, "Bags")

        val rows = dao.getSubtasks(task)
        assertEquals(listOf("Tickets", "Hotel", "Bags"), rows.map { it.title })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
        assertEquals(listOf(first, last), listOf(rows.first().id, rows.last().id))
        assertEquals(32, rows.last().uid.length)
    }

    @Test
    fun renameAndDeleteTouchOneRow() = runBlocking {
        val task = dao.insertTask(Task(title = "Trip"))
        listOf("a", "b", "c").forEach { dao.appendSubtask(task, it) }
        val (a, b, c) = dao.getSubtasks(task)

        assertEquals(1, dao.renameSubtask(b.id, "B"))
        assertEquals(1, dao.deleteSubtaskById(a.id))

        assertEquals(listOf(b.copy(title = "B"), c), dao.getSubtasks(task))
        assertEquals(0, dao.renameSubtask(a.id, "gone"))
        assertEquals(0, dao.deleteSubtaskById(a.id))
    }

    @Test
    fun restoreSubtaskPutsItBackAtItsPositionWithItsUid() = runBlocking {
        val task = dao.insertTask(Task(title = "Trip"))
        listOf("a", "b", "c").forEach { dao.appendSubtask(task, it) }
        val b = dao.getSubtasks(task)[1].copy(isCompleted = true)
        dao.setSubtaskCompleted(b.id, done = true)
        dao.deleteSubtaskById(b.id)
        // Positions were compacted meanwhile (the sheet saved): b's slot is taken by c.
        dao.replaceSubtasks(task, dao.getSubtasks(task))

        dao.restoreSubtask(b)

        val rows = dao.getSubtasks(task)
        assertEquals(listOf("a", "b", "c"), rows.map { it.title })
        assertEquals(b.uid, rows[1].uid)
        assertTrue(rows[1].isCompleted)
    }

    @Test
    fun undoPutsTheTaskBackWithTheSameSubtasks() = runBlocking {
        val id = dao.insertTask(Task(title = "Shop"))
        dao.replaceSubtasks(id, listOf(Subtask(taskId = id, title = "Milk", isCompleted = true)))
        val task = dao.getTaskById(id)!!
        val subtasks = dao.getSubtasks(id)
        dao.deleteTaskById(id)

        dao.restoreTask(task, subtasks)

        assertEquals(task, dao.getTaskById(id))
        assertEquals(subtasks, dao.getSubtasks(id))
    }
}
