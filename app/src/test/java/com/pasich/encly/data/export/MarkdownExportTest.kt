package com.pasich.encly.data.export

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.backup.VaultSnapshot
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.data.zip.zipWriter
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MarkdownExportTest {

    private val labels = MarkdownLabels(
        untitled = "Untitled",
        unreadable = "Unreadable",
        tasks = "Tasks",
        priorities = mapOf(0 to "Low", 1 to "Medium", 2 to "High"),
    )

    private fun body(vararg blocks: Block) = BlockConverter.blocksToJson(blocks.toList())
    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun note(id: Long, title: String, vararg blocks: Block, block: Note.() -> Unit = {}) =
        Note(id = id, title = title, value = body(*blocks), date = 1_700_000_000_000, dateCreate = 1_690_000_000_000)
            .apply(block)

    /** The zip's entries by name, in order. */
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                assertTrue("duplicate entry ${entry.name}", entry.name !in entries)
                entries[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
            }
        }
        return entries
    }

    private fun exportAll(snapshot: VaultSnapshot): Map<String, String> {
        val out = Buffer()
        MarkdownExport.writeAll(snapshot, labels, out)
        return unzip(out.readByteArray())
    }

    @Test
    fun theZipHoldsOneFilePerNoteAndATasksFile() {
        val files = exportAll(
            VaultSnapshot(
                notes = listOf(note(1, "Groceries", text("milk")), note(2, "Ideas", text("one"))),
                tags = emptyList(),
                tasks = emptyList(),
            ),
        )

        assertEquals(setOf("tasks.md", "Groceries.md", "Ideas.md"), files.keys)
        assertTrue(files.getValue("Groceries.md").endsWith("# Groceries\n\nmilk\n"))
    }

    @Test
    fun archivedNotesGoToTheArchiveFolderAndTrashedOnesAreLeftOut() {
        val files = exportAll(
            VaultSnapshot(
                notes = listOf(
                    note(1, "Live", text("a")),
                    note(2, "Old", text("b")) { isArchived = true },
                    note(3, "Gone", text("c")) { isTrash = true },
                    note(4, "Old and gone", text("d")) {
                        isArchived = true
                        isTrash = true
                    },
                ),
                tags = emptyList(),
                tasks = emptyList(),
            ),
        )

        assertEquals(setOf("tasks.md", "Live.md", "archive/Old.md"), files.keys)
        assertFalse(files.values.any { it.contains("Gone") })
    }

    @Test
    fun notesWithTheSameTitleOrAReservedNameGetUniqueFiles() {
        val files = exportAll(
            VaultSnapshot(
                notes = listOf(
                    note(1, "Plan", text("1")),
                    note(2, "plan", text("2")),
                    note(3, "tasks", text("3")),
                    note(4, "", text("4")),
                    note(5, "../../etc/passwd", text("5")),
                    note(6, "Plan", text("6")) { isArchived = true },
                ),
                tags = emptyList(),
                tasks = emptyList(),
            ),
        )

        assertEquals(
            setOf(
                "tasks.md",
                "Plan.md",
                "plan (2).md",
                "tasks (2).md",
                "Untitled.md",
                "etcpasswd.md",
                "archive/Plan.md",
            ),
            files.keys,
        )
        assertTrue(files.keys.none { it.startsWith("/") || ".." in it })
    }

    @Test
    fun theTagsAreInTheFrontMatterAndHiddenTagsAreIncluded() {
        val files = exportAll(
            VaultSnapshot(
                notes = listOf(note(1, "Secret plan", text("x")) { tagId = 7 }),
                tags = listOf(Tag(id = 7, nameTag = "Private", isVisible = false)),
                tasks = emptyList(),
            ),
        )

        assertTrue(files.getValue("Secret plan.md").contains("tags: [\"Private\"]\n"))
    }

    @Test
    fun theTasksFileHasCheckboxesSubtasksAndPriorities() {
        val tasks = listOf(
            Task(id = 1, title = "Buy milk", description = "Two litres", priority = 2, categoryId = 7, position = 1),
            Task(id = 2, title = "Call mom", isCompleted = true, priority = 0, position = 0),
            Task(id = 3, title = "Pay rent", priority = 1, position = 2),
        )
        val subtasks = listOf(
            Subtask(id = 1, taskId = 1, title = "Oat", position = 0),
            Subtask(id = 2, taskId = 1, title = "Soy", isCompleted = true, position = 1),
        )
        val files = exportAll(
            VaultSnapshot(
                notes = emptyList(),
                tags = listOf(Tag(id = 7, nameTag = "Home")),
                tasks = tasks,
                subtasks = subtasks,
            ),
        )

        assertEquals(
            "# Tasks\n\n" +
                "- [ ] Buy milk (High, Home)\n" +
                "  > Two litres\n" +
                "  - [ ] Oat\n" +
                "  - [x] Soy\n" +
                "- [ ] Pay rent (Medium)\n" +
                "- [x] Call mom (Low)\n",
            files.getValue("tasks.md"),
        )
    }

    @Test
    fun anEmptyVaultStillWritesAValidZip() {
        val files = exportAll(VaultSnapshot(emptyList(), emptyList(), emptyList()))

        assertEquals(setOf("tasks.md"), files.keys)
        assertEquals("# Tasks\n\n", files.getValue("tasks.md"))
    }

    @Test
    fun aSingleNoteIsOneMarkdownDocument() {
        val snapshot = VaultSnapshot(
            notes = listOf(
                note(1, "One", text("first")),
                note(2, "Two", text("second")),
                note(3, "Trashed", text("x")) { isTrash = true },
            ),
            tags = emptyList(),
            tasks = emptyList(),
        )
        val out = Buffer()

        assertTrue(MarkdownExport.writeNote(snapshot, 2, labels, out))
        val markdown = out.readString()

        assertTrue(markdown.startsWith("---\ntitle: \"Two\"\n"))
        assertTrue(markdown.endsWith("# Two\n\nsecond\n"))
        assertFalse(markdown.contains("first"))
        assertFalse(MarkdownExport.writeNote(snapshot, 3, labels, Buffer()))
        assertFalse(MarkdownExport.writeNote(snapshot, 99, labels, Buffer()))
    }

    @Test
    fun aManyNoteVaultKeepsEveryFileUnique() {
        val notes = (1L..200L).map { note(it, "Same title", text("n$it")) }
        val files = exportAll(VaultSnapshot(notes, emptyList(), emptyList()))

        assertEquals(201, files.size)
        assertNotNull(files["Same title (200).md"])
    }

    @Test
    fun theBlocksOfEveryTypeAreInTheFile() {
        val items = MutableStateFlow(listOf(ItemListBlock("x", isCheck = true)))
        val markdown = MarkdownNotes.render(
            note(1, "All", text("t"), Block.ListBlock(items, BlockType.LIST_CHECK), Block.SeparatorBlock()),
            null,
            labels,
        )

        assertTrue(markdown.endsWith("# All\n\nt\n\n- [x] x\n\n---\n"))
    }

    // The zip's bytes: the same entries written with plain java.util.zip, at a fixed clock and time zone.

    private val zone = TimeZone.getDefault()

    @Before
    fun fixTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restoreTimeZone() = TimeZone.setDefault(zone)

    private fun goldenSnapshot(): VaultSnapshot {
        val notes = listOf(
            note(1, "Groceries", text("milk")),
            note(2, "\u041f\u0440\u0438\u0432\u0456\u0442", text("emoji \uD83D lone surrogate")) { tagId = 7 },
            note(3, "Old", text("b")) { isArchived = true },
            note(4, "Same", text("1")) { date = EPOCH_1980 },
            note(5, "Same", text("2")) { date = EPOCH_1980 - 1 },
            note(6, "Gone", text("c")) { isTrash = true },
        )
        val tags = listOf(Tag(id = 7, nameTag = "Home"))
        val tasks = listOf(Task(id = 1, title = "Buy milk", priority = 1, position = 0))
        return VaultSnapshot(notes, tags, tasks)
    }

    private fun goldenZip(snapshot: VaultSnapshot): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, time: Long?, text: String) {
                zip.putNextEntry(ZipEntry(name).apply { this.time = time ?: CLOCK })
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            fun render(id: Long): String {
                val note = snapshot.notes.first { it.id == id }
                return MarkdownNotes.render(note, snapshot.tags.firstOrNull { it.id == note.tagId }?.nameTag, labels)
            }
            put("tasks.md", null, MarkdownTasks.render(snapshot.tasks, snapshot.subtasks, snapshot.tags, labels))
            put("Groceries.md", 1_700_000_000_000, render(1))
            put("$CYRILLIC.md", 1_700_000_000_000, render(2))
            put("archive/Old.md", 1_700_000_000_000, render(3))
            put("Same.md", EPOCH_1980, render(4))
            // Before 1980 a zip entry has no usable time: it gets the clock's.
            put("Same (2).md", null, render(5))
        }
        return out.toByteArray()
    }

    @Test
    fun theZipIsByteForByteWhatPlainJavaUtilZipWrites() {
        val snapshot = goldenSnapshot()
        val out = Buffer()

        MarkdownExport.writeAll(snapshot, labels, zipWriter(out) { CLOCK })

        assertArrayEquals(goldenZip(snapshot), out.readByteArray())
    }

    @Test
    fun theZipIsCompleteOnceWriteAllReturnsWithoutClosingTheSink() {
        val bytes = ByteArrayOutputStream()
        val sink = bytes.asSink().buffered()

        MarkdownExport.writeAll(goldenSnapshot(), labels, sink)

        assertEquals(
            setOf("tasks.md", "Groceries.md", "$CYRILLIC.md", "archive/Old.md", "Same.md", "Same (2).md"),
            unzip(bytes.toByteArray()).keys,
        )
    }

    @Test
    fun aSingleNoteIsItsUtf8BytesWithALoneSurrogateAsQuestionMark() {
        val snapshot = VaultSnapshot(listOf(note(1, "One", text("a \uD83D b"))), emptyList(), emptyList())
        val out = Buffer()

        MarkdownExport.writeNote(snapshot, 1, labels, out)

        assertArrayEquals(
            MarkdownNotes.render(snapshot.notes.single(), null, labels).toByteArray(Charsets.UTF_8),
            out.readByteArray(),
        )
    }

    private companion object {
        const val EPOCH_1980 = 315_532_800_000L
        const val CLOCK = 1_800_000_000_000L
        const val CYRILLIC = "\u041f\u0440\u0438\u0432\u0456\u0442"
    }
}
