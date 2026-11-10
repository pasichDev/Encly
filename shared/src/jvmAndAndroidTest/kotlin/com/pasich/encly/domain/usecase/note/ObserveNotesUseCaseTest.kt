package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNotesUseCaseTest {

    @Test
    fun aDatabaseReadFailureFailsTheFlowInsteadOfEmittingNoNotes() = runTest {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getAllNotesWithTag(): Flow<List<NoteWithTags>> = flow { throw IOException("vault closed") }
        }

        val emitted = mutableListOf<List<NoteListItem>>()
        val thrown = runCatching {
            ObserveNotesUseCase(failing, StandardTestDispatcher(testScheduler))(
                tagId = null,
                sortOption = NoteSortOption.UPDATED_DESC,
            ).toList(emitted)
        }.exceptionOrNull()

        // The screen turns the failure into an error state (asLoadState); never an empty list.
        assertTrue(thrown is IOException)
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun allNotesLeaveOutHiddenTagsAndFollowTheSortOption() = runTest {
        val hidden = Tag(id = 1, nameTag = "Private", isVisible = false)
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(
                noteWithTag(id = 1, date = 10),
                noteWithTag(id = 2, date = 30),
                noteWithTag(id = 3, date = 20, tag = hidden),
            )
        }

        val newestFirst = loaded(repository, NoteSortOption.UPDATED_DESC)
        val oldestFirst = loaded(repository, NoteSortOption.UPDATED_ASC)

        assertEquals(listOf(2L, 1L), newestFirst.map { it.note.id })
        assertEquals(listOf(1L, 2L), oldestFirst.map { it.note.id })
    }

    @Test
    fun pinnedNotesComeFirstWhateverTheSortOption() = runTest {
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(
                noteWithTag(id = 1, date = 10),
                noteWithTag(id = 2, date = 30),
                noteWithTag(id = 3, date = 20, pinned = true),
                noteWithTag(id = 4, date = 5, pinned = true),
            )
        }

        assertEquals(listOf(3L, 4L, 2L, 1L), loaded(repository, NoteSortOption.UPDATED_DESC).map { it.note.id })
        assertEquals(listOf(4L, 3L, 1L, 2L), loaded(repository, NoteSortOption.UPDATED_ASC).map { it.note.id })
    }

    @Test
    fun titleOrdersKeepPinnedFirstAndPutUntitledNotesLast() = runTest {
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(
                noteWithTag(id = 1, date = 10, title = "beta"),
                noteWithTag(id = 2, date = 30, title = ""),
                noteWithTag(id = 3, date = 20, title = "Zulu", pinned = true),
                noteWithTag(id = 4, date = 5, title = "alpha"),
                noteWithTag(id = 5, date = 6, title = "Yankee", pinned = true),
            )
        }

        assertEquals(listOf(5L, 3L, 4L, 1L, 2L), loaded(repository, NoteSortOption.TITLE_ASC).map { it.note.id })
        assertEquals(listOf(3L, 5L, 1L, 4L, 2L), loaded(repository, NoteSortOption.TITLE_DESC).map { it.note.id })
    }

    @Test
    fun theArchiveIsReadFromItsOwnQueryAndLeavesOutHiddenTags() = runTest {
        val hidden = Tag(id = 1, nameTag = "Private", isVisible = false)
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(noteWithTag(id = 1, date = 1))
            archivedNotesWithTags.value = listOf(
                noteWithTag(id = 2, date = 10),
                noteWithTag(id = 3, date = 20),
                noteWithTag(id = 4, date = 30, tag = hidden),
            )
        }

        val archived = ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))(
            tagId = null,
            sortOption = NoteSortOption.UPDATED_DESC,
            archived = true,
        ).first()

        assertEquals(listOf(3L, 2L), archived.map { it.note.id })
    }

    private fun archivedWithTag(id: Long, tag: Tag) = NoteWithTags(
        Note(id = id, title = "a$id", date = id, tagIds = setOf(tag.id), isArchived = true),
        listOf(tag),
    )

    @Test
    fun aHiddenTagsViewListsItsArchivedNotesAfterTheOthers() = runTest {
        val hidden = Tag(id = 5, nameTag = "Private", isVisible = false)
        val repository = TestNotesRepository().apply {
            tagFlow(5).value =
                listOf(noteWithTag(id = 1, date = 10, tag = hidden), noteWithTag(id = 2, date = 1, tag = hidden))
            archivedNotesWithTags.value =
                listOf(archivedWithTag(3, hidden), archivedWithTag(4, Tag(id = 6, isVisible = true)))
        }

        val items = ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))(
            tagId = 5,
            sortOption = NoteSortOption.UPDATED_DESC,
        ).first()

        assertEquals(listOf(1L, 2L, 3L), items.map { it.note.id })
    }

    @Test
    fun aVisibleTagsViewLeavesItsArchivedNotesToTheArchive() = runTest {
        val visible = Tag(id = 5, nameTag = "Work", isVisible = true)
        val repository = TestNotesRepository().apply {
            tagFlow(5).value = listOf(noteWithTag(id = 1, date = 10, tag = visible))
            archivedNotesWithTags.value = listOf(archivedWithTag(3, visible))
        }

        val items = ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))(
            tagId = 5,
            sortOption = NoteSortOption.UPDATED_DESC,
        ).first()

        assertEquals(listOf(1L), items.map { it.note.id })
    }

    @Test
    fun aNoteWithAHiddenTagShowsOnlyInTheHiddenTagsViewNotInItsVisibleTagsOrTheLists() = runTest {
        val visible = Tag(id = 5, nameTag = "Work", position = 1)
        val hidden = Tag(id = 6, nameTag = "Private", isVisible = false, position = 0)
        val both = NoteWithTags(Note(id = 1, title = "both", date = 10, tagIds = setOf(5, 6)), listOf(hidden, visible))
        val workOnly = noteWithTag(id = 2, date = 5, tag = visible)
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(both, workOnly)
            tagFlow(5).value = listOf(both, workOnly)
            tagFlow(6).value = listOf(both)
        }
        val useCase = ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))

        assertEquals(listOf(2L), useCase(null, NoteSortOption.UPDATED_DESC).first().map { it.note.id })
        assertEquals(listOf(2L), useCase(5, NoteSortOption.UPDATED_DESC).first().map { it.note.id })
        val inHidden = useCase(6, NoteSortOption.UPDATED_DESC).first()
        assertEquals(listOf(1L), inHidden.map { it.note.id })
        // The item carries every tag of the note.
        assertEquals(listOf("Private", "Work"), inHidden.single().tags.map { it.nameTag })
    }

    @Test
    fun theArchiveLeavesOutANoteWithAnyHiddenTag() = runTest {
        val visible = Tag(id = 5, nameTag = "Work")
        val hidden = Tag(id = 6, nameTag = "Private", isVisible = false)
        val repository = TestNotesRepository().apply {
            archivedNotesWithTags.value = listOf(
                NoteWithTags(Note(id = 1, date = 1, isArchived = true), listOf(visible, hidden)),
                NoteWithTags(Note(id = 2, date = 2, isArchived = true), listOf(visible)),
            )
        }

        val archived = ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))(
            tagId = null,
            sortOption = NoteSortOption.UPDATED_DESC,
            archived = true,
        ).first()

        assertEquals(listOf(2L), archived.map { it.note.id })
    }

    @Test
    fun oneUnreadableNoteDoesNotTakeTheListDown() = runTest {
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(
                noteWithTag(id = 1, date = 2, value = """[{"blockType":"TEXT","text":"fine"}]"""),
                noteWithTag(id = 2, date = 1, value = """[{"blockType":"FUTURE"}]"""),
            )
        }

        val items = loaded(repository, NoteSortOption.UPDATED_DESC)

        assertEquals("fine", items[0].preview)
        assertNull(items[1].preview)
    }

    private suspend fun TestScope.loaded(repository: NotesRepository, sortOption: NoteSortOption): List<NoteListItem> =
        ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))(null, sortOption).first()

    private fun noteWithTag(
        id: Long,
        date: Long,
        tag: Tag? = null,
        value: String = "",
        pinned: Boolean = false,
        title: String = "n$id",
    ) = NoteWithTags(
        Note(
            id = id,
            title = title,
            value = value,
            date = date,
            dateCreate = date,
            tagIds = setOfNotNull(tag?.id),
            isPinned = pinned,
        ),
        listOfNotNull(tag),
    )
}
