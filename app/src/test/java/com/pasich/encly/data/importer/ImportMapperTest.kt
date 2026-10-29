package com.pasich.encly.data.importer

import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.data.backup.BackupImporter
import com.pasich.encly.data.backup.ImportMode
import com.pasich.encly.data.backup.TagMatch
import com.pasich.encly.data.importer.ImportFixtures.NOW
import com.pasich.encly.editor.model.Block
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMapperTest {
    private fun text(value: String): Block = Block.TextBlock(text = MutableStateFlow(value))

    private fun map(vararg notes: ImportedNote, source: ImportSource = ImportSource.MARKDOWN): ImportPlan =
        ImportMapper.map(ParsedNotes(notes.toList(), ImportSkips()), source, ReadSkips(), NOW)

    private fun note(title: String, body: String, key: String? = null, tags: List<String> = emptyList()) =
        ImportedNote(sourceKey = key, title = title, blocks = listOf(text(body)), tags = tags)

    private val keepFiles = ImportFixtures.textSource(
        "a.json" to """{"createdTimestampUsec":1000000000000000,"userEditedTimestampUsec":1000000001000000,""" +
            """"title":"A","textContent":"alpha","labels":[{"name":"Work"}]}""",
        "b.json" to """{"createdTimestampUsec":1000000002000000,"userEditedTimestampUsec":1000000003000000,""" +
            """"title":"B","textContent":"beta"}""",
    )

    @Test
    fun theMappedPayloadIsValidAndUsesEnclysRecords() {
        val plan = ImportFixtures.ready(keepFiles)
        BackupPayloadCodec.validate(plan.payload)
        assertEquals(listOf("A", "B"), plan.payload.notes.map { it.title })
        assertEquals(listOf("Work"), plan.payload.tags.map { it.name })
        assertEquals(1_000_000_001_000L, plan.payload.notes[0].date)
        assertEquals(1_000_000_000_000L, plan.payload.notes[0].dateCreate)
        assertTrue(plan.payload.tasks.isEmpty())
        assertTrue(plan.payload.notes.all { it.uid.length == 64 })
    }

    @Test
    fun aMissingDateFallsBackToTheOtherOrToNow() {
        val plan = map(
            ImportedNote("a", "only created", listOf(text("x")), created = 5),
            ImportedNote("b", "only edited", listOf(text("x")), edited = 7),
            ImportedNote("c", "none", listOf(text("x"))),
        )
        assertEquals(listOf(5L to 5L, 7L to 7L, NOW to NOW), plan.payload.notes.map { it.date to it.dateCreate })
    }

    @Test
    fun importingTheSameExportTwiceAddsNothingTheSecondTime() = runTest {
        val store = InMemoryVaultDataStore()
        val first = ImportFixtures.ready(keepFiles)
        val one = BackupImporter.import(first.payload, ImportMode.MERGE, store, TagMatch.UID_OR_NAME)
        assertEquals(2, one.notesAdded)

        val again = ImportFixtures.ready(keepFiles)
        assertEquals(first.payload.notes.map { it.uid }, again.payload.notes.map { it.uid })
        val two = BackupImporter.import(again.payload, ImportMode.MERGE, store, TagMatch.UID_OR_NAME)
        assertEquals(0, two.notesAdded)
        assertEquals(0, two.tagsAdded)
        assertEquals(2, store.notes.size)
        assertEquals(1, store.tags.size)
    }

    @Test
    fun theSameTextWithDifferentTitlesOrPathsStaysDistinct() {
        val byTitle = map(note("One", "same body"), note("Two", "same body"))
        assertEquals(2, byTitle.payload.notes.size)
        val byPath = map(note("Same", "same body", key = "a/Same.md"), note("Same", "same body", key = "b/Same.md"))
        assertEquals(2, byPath.payload.notes.size)
    }

    @Test
    fun theSameKeyOrTheSameTitleAndTextCollapsesAndIsCounted() {
        val plan =
            map(note("T", "body  here"), note("t", "BODY here"), note("X", "x", key = "k"), note("Y", "y", key = "k"))
        assertEquals(2, plan.payload.notes.size)
        assertEquals(2, plan.preview.duplicatesInSelection)
    }

    @Test
    fun theUidDependsOnTheSource() {
        val a = map(note("T", "b", key = "k"), source = ImportSource.JOPLIN).payload.notes.single().uid
        val b = map(note("T", "b", key = "k"), source = ImportSource.NOTESNOOK).payload.notes.single().uid
        assertNotEquals(a, b)
    }

    @Test
    fun theFirstTagIsKeptNamesAreMatchedAndCutLikeTheTagEditor() {
        val plan = map(
            note("a", "x", tags = listOf("  Work   Stuff ", "second")),
            note("b", "x2", tags = listOf("work stuff")),
            note("c", "x3", tags = listOf("a".repeat(40))),
        )
        assertEquals(listOf("Work Stuff", "a".repeat(20)), plan.payload.tags.map { it.name })
        val uids = plan.payload.notes.map { it.tagUid }
        assertEquals(uids[0], uids[1])
        assertNotEquals(uids[0], uids[2])
    }

    @Test
    fun aTagTheVaultAlreadyHasIsReusedByNameNotDuplicated() = runTest {
        val store = InMemoryVaultDataStore()
        store.insertTag(com.pasich.encly.data.model.Tag(nameTag = "WORK", position = 0, uid = "existing"))
        BackupImporter.import(ImportFixtures.ready(keepFiles).payload, ImportMode.MERGE, store, TagMatch.UID_OR_NAME)
        assertEquals(1, store.tags.size)
        assertEquals(store.tags.single().id, store.notes.first { it.title == "A" }.tagId)
    }

    @Test
    fun onlyTagsOfImportedNotesAreCreated() {
        val plan = map(ImportedNote("k", "T", listOf(text("x")), tags = emptyList()))
        assertTrue(plan.payload.tags.isEmpty())
    }

    @Test
    fun selectionKeepsOnlyChosenNotesAndTheirTags() {
        val plan = ImportFixtures.ready(keepFiles)
        val b = plan.payload.notes.first { it.title == "B" }.uid
        val chosen = ImportReview.selection(plan.payload, setOf(b))
        assertEquals(listOf("B"), chosen.notes.map { it.title })
        assertTrue(chosen.tags.isEmpty())
        BackupPayloadCodec.validate(chosen)
    }

    @Test
    fun anImportThatFindsNoNoteIsNothingFound() {
        val analysis = ImportFixtures.analyze(
            ImportFixtures.textSource("a.json" to """{"createdTimestampUsec":1,"userEditedTimestampUsec":1}"""),
        )
        assertEquals(Analysis.Failed(ImportError.NOTHING_FOUND), analysis)
    }

    @Test
    fun tooManyNotesIsRefusedEvenWhenTheFilesAreSmall() {
        val limits = ImportLimits(maxNotes = 1)
        val read = ImportFixtures.read(keepFiles)
        assertEquals(Analysis.Failed(ImportError.TOO_LARGE), ImportAnalyzer.analyze(read, limits = limits))
    }

    @Test
    fun aNestedArchiveAndAnUnsafePathAreReportedInThePreview() {
        val inner = ImportFixtures.zipText("x.md" to "x")
        val outer = ImportFixtures.zip("../evil.md" to ByteArray(1), "in.zip" to inner, "ok.md" to "ok".toByteArray())
        val plan = ImportFixtures.ready(ImportFixtures.source("o.zip" to outer))
        assertEquals(1, plan.preview.nestedArchives)
        assertEquals(1, plan.preview.filesSkipped)
        assertEquals(1, plan.preview.notes)
    }

    @Test
    fun invalidUtf8NotesAreReportedAndTheRestImports() {
        val plan = ImportFixtures.ready(
            ImportFixtures.source("bad.md" to byteArrayOf(0xc3.toByte(), 0x28), "good.md" to "fine".toByteArray()),
        )
        assertEquals(1, plan.preview.notes)
        assertEquals(1, plan.preview.filesSkipped)
    }

    @Test
    fun nothingReadBecauseOfTheLimitsOrUnreadableFilesIsSaidSo() {
        val tooBig = ImportLimits(maxEntryBytes = 10, maxContainerBytes = 10)
        val big = ImportFixtures.read(ImportFixtures.textSource("a.md" to "x".repeat(50)), tooBig)
        assertEquals(Analysis.Failed(ImportError.TOO_LARGE), ImportAnalyzer.analyze(big))

        val broken = ImportFileSource { sequenceOf(SourceFile("a.md") { throw java.io.IOException("x") }) }
        assertEquals(Analysis.Failed(ImportError.UNREADABLE), ImportAnalyzer.analyze(ImportFixtures.read(broken)))

        val none = ImportFixtures.read(ImportFixtures.textSource())
        assertEquals(Analysis.Failed(ImportError.NOTHING_FOUND), ImportAnalyzer.analyze(none))
    }

    @Test
    fun twoMarkdownFilesWithTheSameNameButDifferentTextAreTwoNotes() {
        val plan = ImportFixtures.ready(ImportFixtures.textSource("todo.md" to "milk", "other/todo.md" to "bread"))
        assertEquals(2, plan.payload.notes.size)
        assertEquals(0, plan.preview.duplicatesInSelection)
    }

    @Test
    fun anEditedMarkdownFileIsANewNoteAndAnIdenticalOneIsTheSame() {
        fun uid(text: String) =
            ImportFixtures.ready(ImportFixtures.textSource("Inbox/todo.md" to text)).payload.notes.single().uid
        assertEquals(uid("# Todo\nmilk"), uid("# Todo\nmilk"))
        assertEquals(uid("# Todo\nmilk"), uid("# Todo\n  Milk\n"))
        assertNotEquals(uid("# Todo\nmilk"), uid("# Todo\nmilk and bread"))
    }

    @Test
    fun theNotesnookRulesApplyOnlyToTheFilesThatAreNotesnooks() {
        val notesnook = "---\ntitle: A\ncreated_at: 2024-01-01\nupdated_at: 2024-01-02\npinned: true\n---\nbody"
        val plan = ImportFixtures.ready(
            ImportFixtures.textSource(
                "a.md" to notesnook,
                "docs/attachments/b.md" to "# B\ntext",
                "c.txt" to "First line\nrest",
            ),
        )
        assertEquals(ImportSource.NOTESNOOK, plan.preview.source)
        assertEquals(setOf("A", "B", "c"), plan.payload.notes.map { it.title }.toSet())
        assertEquals(0, plan.preview.losses.attachments)
        assertEquals(1, plan.preview.losses.pinned)
        assertTrue(plan.rows.single { it.title == "c" }.snippet.startsWith("First line"))
    }

    @Test
    fun theTagOfEveryRowIsItsOwnTagName() {
        val plan = map(
            note("A", "a", key = "1", tags = listOf("Work")),
            note("B", "b", key = "2", tags = listOf("home")),
            note("C", "c", key = "3", tags = listOf("work")),
        )
        assertEquals(listOf("Work", "home", "Work"), plan.rows.map { it.tag })
    }
}
