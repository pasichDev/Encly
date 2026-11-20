package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.entry
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDetectorTest {
    private fun detect(vararg entries: ImportEntry) = FormatDetector.detect(entries.toList())

    private val keep = """{"createdTimestampUsec":1,"userEditedTimestampUsec":2,"title":"t","textContent":"x"}"""
    private val sn = """{"version":"004","items":[""" +
        """{"content_type":"Note","uuid":"u","content":{"title":"t","text":"x"}}]}"""
    private val joplin = "Title\n\nBody\n\nid: 0123456789abcdef0123456789abcdef\nparent_id: \ntype_: 1"
    private val notesnook = "---\ntitle: \"T\"\ncreated_at: 2024-01-01T00:00:00Z\n" +
        "updated_at: 2024-01-02T00:00:00Z\n---\n\n# T\n\nbody"

    @Test
    fun recognisesEachSourceByContent() {
        assertEquals(Detection.Found(ImportSource.GOOGLE_KEEP), detect(entry("Takeout/Keep/a.json", keep)))
        assertEquals(Detection.Found(ImportSource.STANDARD_NOTES), detect(entry("b.json", sn)))
        assertEquals(Detection.Found(ImportSource.JOPLIN), detect(entry("x.md", joplin)))
        assertEquals(Detection.Found(ImportSource.NOTESNOOK), detect(entry("x.md", notesnook)))
        assertEquals(Detection.Found(ImportSource.MARKDOWN), detect(entry("x.md", "# hello\n\nworld")))
        assertEquals(Detection.Found(ImportSource.MARKDOWN), detect(entry("x.txt", "plain")))
    }

    @Test
    fun theExtensionAloneDecidesNothing() {
        // JSON named .txt, Keep note named .md: only content counts.
        val backup = entry("Standard Notes Backup and Import File.txt", sn)
        assertEquals(Detection.Found(ImportSource.STANDARD_NOTES), detect(backup))
        assertEquals(Detection.Unsupported, detect(entry("note.json", "not json at all")))
        assertEquals(Detection.Found(ImportSource.MARKDOWN), detect(entry("keep.md", keep)))
    }

    @Test
    fun lookAlikesAreNotGuessed() {
        assertEquals(Detection.Unsupported, detect(entry("a.json", """{"foo":"bar"}""")))
        assertEquals(Detection.Unsupported, detect(entry("a.json", "[1,2,3]")))
        // Keep-like with a missing key, or a non-numeric one.
        assertEquals(Detection.Unsupported, detect(entry("a.json", """{"createdTimestampUsec":1,"title":"x"}""")))
        assertEquals(
            Detection.Unsupported,
            detect(entry("a.json", """{"createdTimestampUsec":"1","userEditedTimestampUsec":"2"}""")),
        )
        // {version, items} without typed items.
        assertEquals(Detection.Unsupported, detect(entry("a.json", """{"version":"1","items":[]}""")))
        // A Joplin-like block without the id.
        val noId = entry("a.md", "T\n\nbody\n\nparent_id: x\ntype_: 1")
        assertEquals(Detection.Found(ImportSource.MARKDOWN), detect(noId))
    }

    @Test
    fun encryptedStandardNotesIsRefused() {
        val byKeyParams = """{"version":"004","items":[],"keyParams":{"identifier":"a"}}"""
        val legacy = """{"version":"003","items":[],"auth_params":{"x":1}}"""
        val byContent = """{"version":"004","items":[{"content_type":"Note","uuid":"u","content":"004:abc"}]}"""
        listOf(byKeyParams, legacy, byContent).forEach {
            assertEquals(Detection.Encrypted(ImportSource.STANDARD_NOTES), detect(entry("b.json", it)))
        }
    }

    @Test
    fun notesnookBackupsAreRefused() {
        assertEquals(Detection.Encrypted(ImportSource.NOTESNOOK), detect(entry("my.nnbackup", "x")))
        assertEquals(Detection.Encrypted(ImportSource.NOTESNOOK), detect(entry(".nnbackup", "")))
        val chunk = """{"version":1,"type":"backup","date":1,"data":"abc","hash":"x",""" +
            """"hash_type":"md5","compressed":true,"encrypted":false}"""
        assertEquals(Detection.Encrypted(ImportSource.NOTESNOOK), detect(entry("backup/chunk.json", chunk)))
    }

    @Test
    fun filesOfSeveralAppsAreAmbiguous() {
        assertEquals(Detection.Ambiguous, detect(entry("a.json", keep), entry("b.md", joplin)))
        assertEquals(Detection.Ambiguous, detect(entry("a.json", sn), entry("b.md", notesnook)))
    }

    @Test
    fun plainTextBesideASpecificExportDoesNotMakeItAmbiguous() {
        // Keep's Takeout has a Labels.txt beside the notes.
        val labels = entry("Takeout/Keep/Labels.txt", "a\nb")
        assertEquals(Detection.Found(ImportSource.GOOGLE_KEEP), detect(labels, entry("Takeout/Keep/n.json", keep)))
    }

    @Test
    fun theItemsFolderBesideAStandardNotesBackupIsIgnored() {
        val detection = detect(
            entry("Standard Notes Backup and Import File.txt", sn),
            entry("Items/Note/Plan-1234.txt", "text of the note"),
        )
        assertEquals(Detection.Found(ImportSource.STANDARD_NOTES), detection)
    }

    @Test
    fun hiddenFoldersAndMediaAreIgnored() {
        val detection = detect(
            entry(".obsidian/app.json", """{"x":1}"""),
            entry("img.png", "x"),
            entry("page.html", "<p>x</p>"),
            entry("note.md", "text"),
        )
        assertEquals(Detection.Found(ImportSource.MARKDOWN), detection)
    }

    @Test
    fun nothingUsableIsNotGuessed() {
        assertEquals(Detection.Empty, detect())
        assertEquals(Detection.Unsupported, detect(entry("img.png", "x")))
        assertEquals(Detection.Unsupported, detect(entry("data.csv", "a,b")))
        val badBytes = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x41)
        assertEquals(Detection.Unsupported, detect(ImportEntry("bad.md", badBytes)))
    }

    @Test
    fun aManualChoiceStillHasToValidate() {
        val kinds = FormatDetector.classify(listOf(entry("a.json", sn)), ImportLimits())
        val notes = ImportSource.STANDARD_NOTES
        assertEquals(Detection.Found(notes), FormatDetector.validate(notes, kinds))
        assertEquals(Detection.Unsupported, FormatDetector.validate(ImportSource.GOOGLE_KEEP, kinds))
        assertEquals(Detection.Unsupported, FormatDetector.validate(ImportSource.JOPLIN, kinds))
        val locked = entry("a.json", """{"version":"1","items":[],"keyParams":{}}""")
        val encrypted = FormatDetector.classify(listOf(locked), ImportLimits())
        assertEquals(Detection.Encrypted(notes), FormatDetector.validate(notes, encrypted))
        assertEquals(Detection.Empty, FormatDetector.validate(ImportSource.MARKDOWN, emptyList()))
    }
}
