package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.read
import com.pasich.encly.data.importer.ImportFixtures.source
import com.pasich.encly.data.importer.ImportFixtures.tar
import com.pasich.encly.data.importer.ImportFixtures.textSource
import com.pasich.encly.data.importer.ImportFixtures.zip
import com.pasich.encly.data.importer.ImportFixtures.zipText
import kotlinx.io.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ImportReaderTest {

    @Test
    fun readsPlainFilesZipAndTarMembersWithTheirPaths() {
        val result = read(
            source(
                "a.md" to "A".toByteArray(),
                "pack.zip" to zipText("dir/b.md" to "B", "dir/sub/c.txt" to "C"),
                "pack.tar" to tar(TarFile("./d.md", "D".toByteArray()), TarFile("sub/", type = '5')),
            ),
        )
        assertEquals(listOf("a.md", "dir/b.md", "dir/sub/c.txt", "d.md"), result.entries.map { it.path })
        assertEquals("D", String(result.entries.last().bytes))
        assertEquals(0, result.skips.total)
    }

    @Test
    fun aGnuLongNameIsUsed() {
        val longName = "folder/" + "x".repeat(150) + ".md"
        val result = read(source("p.tar" to tar(TarFile(longName, "T".toByteArray()))))
        assertEquals(listOf(longName), result.entries.map { it.path })
    }

    @Test
    fun unsafePathsAreSkippedAndCounted() {
        val names = listOf("../evil.md", "/abs.md", "a/../../b.md", "back\\..\\..\\evil.md", "nul\u0000.md", "C:/x.md")
        val result = read(source("p.zip" to zip(*(names + "ok.md").map { it to ByteArray(1) }.toTypedArray())))
        assertEquals(listOf("ok.md"), result.entries.map { it.path })
        assertEquals(names.size, result.skips.unsafePaths)
    }

    @Test
    fun aPathDeeperThanTheLimitIsUnsafe() {
        val deep = (1..ImportLimits.MAX_PATH_DEPTH + 1).joinToString("/") { "d$it" } + "/n.md"
        val ok = (1..ImportLimits.MAX_PATH_DEPTH - 1).joinToString("/") { "d$it" } + "/n.md"
        val result = read(source("p.zip" to zip(deep to ByteArray(1), ok to ByteArray(1))))
        assertEquals(listOf(ok), result.entries.map { it.path })
        assertEquals(1, result.skips.unsafePaths)
    }

    @Test
    fun tarLinksAndDevicesAreNeverFollowed() {
        val result = read(
            source(
                "p.tar" to tar(
                    TarFile("link.md", type = '2'),
                    TarFile("hard.md", type = '1'),
                    TarFile("dev", type = '3'),
                    TarFile("ok.md", "x".toByteArray()),
                ),
            ),
        )
        assertEquals(listOf("ok.md"), result.entries.map { it.path })
        assertEquals(3, result.skips.unsafePaths)
    }

    @Test
    fun anArchiveInsideAnArchiveIsNotExpanded() {
        val inner = zipText("deep.md" to "deep")
        val result = read(source("outer.zip" to zip("inner.zip" to inner, "keep.md" to "k".toByteArray())))
        assertEquals(listOf("keep.md"), result.entries.map { it.path })
        assertEquals(1, result.skips.nestedArchives)
    }

    @Test
    fun aZipBombStopsAtTheTotalLimit() {
        // 1 MiB of zeros compresses to about a kilobyte; ten of them exceed a 4 MiB total.
        val bomb = ByteArray(1024 * 1024)
        val files = (1..10).map { "f$it.md" to bomb }.toTypedArray()
        val limits = ImportLimits(maxTotalBytes = 4L * 1024 * 1024)
        val error = assertThrows(ImportException::class.java) { read(source("bomb.zip" to zip(*files)), limits) }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun anEntryOverThePerEntryLimitIsSkippedButStillCounted() {
        val big = ByteArray(2048)
        val limits = ImportLimits(maxEntryBytes = 1024)
        val result = read(source("p.zip" to zip("big.md" to big, "small.md" to ByteArray(10))), limits)
        assertEquals(listOf("small.md"), result.entries.map { it.path })
        assertEquals(1, result.skips.tooLarge)
    }

    @Test
    fun aSingleEntryBombHitsTheTotalLimitEvenIfItIsSkipped() {
        val limits = ImportLimits(maxEntryBytes = 1024, maxTotalBytes = 64 * 1024)
        val error = assertThrows(ImportException::class.java) {
            read(source("p.zip" to zip("huge.md" to ByteArray(1024 * 1024))), limits)
        }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun theInputByteLimitCountsWhatIsReallyRead() {
        val limits = ImportLimits(maxInputBytes = 1000)
        val noise = java.util.Random(1).let { r -> ByteArray(5000).also(r::nextBytes) }
        val error = assertThrows(ImportException::class.java) { read(source("n.md" to noise), limits) }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun tooManyEntriesIsTooLarge() {
        val limits = ImportLimits(maxEntries = 5)
        val files = (1..6).map { "n$it.md" to ByteArray(1) }.toTypedArray()
        val error = assertThrows(ImportException::class.java) { read(source("p.zip" to zip(*files)), limits) }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun aJsonExportMayBeLargerThanANoteFile() {
        val json = ByteArray(2048)
        val limits = ImportLimits(maxEntryBytes = 1024, maxContainerBytes = 4096)
        val result = read(source("backup.json" to json, "note.md" to json), limits)
        assertEquals(listOf("backup.json"), result.entries.map { it.path })
        assertEquals(1, result.skips.tooLarge)
    }

    @Test
    fun anUnreadableFileIsCountedNotFatal() {
        val broken = ImportFileSource {
            sequenceOf(
                SourceFile("gone.md") { null },
                SourceFile("io.md") { throw IOException("x") },
                SourceFile("denied.md") { throw SecurityException("x") },
                SourceFile("ok.md") { Buffer().apply { write("ok".toByteArray()) } },
            )
        }
        val result = read(broken)
        assertEquals(listOf("ok.md"), result.entries.map { it.path })
        assertEquals(3, result.skips.unreadable)
    }

    @Test
    fun aTruncatedZipKeepsWhatWasReadAndCountsTheRestAsUnreadable() {
        val noise = java.util.Random(7).let { r -> ByteArray(20_000).also(r::nextBytes) }
        val good = zip("a.md" to "A".toByteArray(), "b.md" to noise)
        val result = read(source("p.zip" to good.copyOf(good.size / 2)))
        assertEquals(listOf("a.md"), result.entries.map { it.path })
        assertEquals(1, result.skips.unreadable)
    }

    @Test
    fun unknownBytesBetweenZipEntriesAreNotTheEndOfTheArchive() {
        val good = zipText("a.md" to "A", "b.md" to "B", "c.md" to "C")
        val second = good.indexOfHeader(from = 1)
        good[second + 2] = 9 // "PK\u0009\u0004": not a header, not a central directory
        val result = read(source("p.zip" to good))
        assertEquals(listOf("a.md"), result.entries.map { it.path })
        assertEquals(1, result.skips.unreadable)
    }

    private fun ByteArray.indexOfHeader(from: Int): Int {
        val magic = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        var found = -1
        var seen = 0
        for (i in 0..size - magic.size) {
            if (magic.indices.all { this[i + it] == magic[it] } && seen++ == from) {
                found = i
                break
            }
        }
        return found
    }

    @Test
    fun aGzipFileIsNotOpened() {
        val result = read(source("a.gz" to byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0)))
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.skips.nestedArchives)
    }

    @Test
    fun cancellationStopsTheRead() {
        val check = { throw IllegalStateException("stop") }
        assertThrows(IllegalStateException::class.java) {
            ImportReader().read(textSource("a.md" to "x"), check)
        }
    }

    @Test
    fun aBackslashIsASeparatorAndOnlyADriveSegmentIsADrive() {
        assertEquals("a/b.md", ImportReader.safePath("a\\b.md"))
        assertEquals("Q: plan.md", ImportReader.safePath("Q: plan.md"))
        assertEquals("notes/C: list.md", ImportReader.safePath("notes/C: list.md"))
        assertNull(ImportReader.safePath("C:/x.md"))
        assertNull(ImportReader.safePath("C:\\x.md"))
        assertNull(ImportReader.safePath("C:"))
        assertNull(ImportReader.safePath("\\\\server\\share\\x.md"))
        assertNull(ImportReader.safePath("a\\..\\..\\b.md"))
    }

    @Test
    fun aZipFromAnOldWindowsToolKeepsItsNotes() {
        val result = read(source("p.zip" to zipText("dir\\sub\\a.md" to "A", "Q: plan.md" to "P")))
        assertEquals(listOf("dir/sub/a.md", "Q: plan.md"), result.entries.map { it.path })
        assertEquals(0, result.skips.unsafePaths)
    }

    @Test
    fun safePathNormalizes() {
        assertEquals("a/b.md", ImportReader.safePath("./a//b.md"))
        assertNull(ImportReader.safePath(".."))
        assertNull(ImportReader.safePath(""))
        assertNull(ImportReader.safePath("a/../b"))
    }

    @Test
    fun aDirectoryEntryWithDataIsSkippedAsUnsafe() {
        val result = read(source("bomb.zip" to directoryZip(count = 1, bytesEach = 8 * 1024 * 1024)))
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.skips.unsafePaths)
    }

    @Test
    fun manyDirectoriesWithDataHitTheTotalLimit() {
        // Each directory inflates 1 MiB of zeros; together they must count against the total.
        val limits = ImportLimits(maxTotalBytes = 4L * 1024 * 1024)
        val error = assertThrows(ImportException::class.java) {
            read(source("bomb.zip" to directoryZip(count = 10, bytesEach = 1024 * 1024)), limits)
        }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun directoryEntriesCountAgainstTheEntryLimit() {
        val limits = ImportLimits(maxEntries = 5)
        val error = assertThrows(ImportException::class.java) {
            read(source("dirs.zip" to directoryZip(count = 6, bytesEach = 0)), limits)
        }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    // --- media and attachments are counted, not kept -------------------------------------------------

    @Test
    fun aBombNamedLikeAPhotoStillEndsAsTooLarge() {
        val limits = ImportLimits(maxSkippedBytes = 1024 * 1024)
        val error = assertThrows(ImportException::class.java) {
            read(source("p.zip" to zip("photo.jpg" to ByteArray(8 * 1024 * 1024), "n.md" to ByteArray(1))), limits)
        }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun mediaBeyondTheTotalLimitIsReportedAsAttachmentsAndTheNotesStillImport() {
        // 4 MiB of photos against a 256 KiB total: only the kept note counts against it.
        val photo = ByteArray(1024 * 1024)
        val limits = ImportLimits(maxTotalBytes = 256L * 1024, maxSkippedBytes = 16L * 1024 * 1024)
        val files = (1..4).map { "Takeout/p$it.jpg" to photo } + ("Takeout/note.md" to "# hi".toByteArray())
        val result = read(source("t.zip" to zip(*files.toTypedArray())), limits)

        assertEquals(listOf("Takeout/note.md"), result.entries.map { it.path })
        assertEquals(4, result.attachments)
        assertEquals(0, result.skips.total)
        val plan = ImportFixtures.ready(source("t.zip" to zip(*files.toTypedArray())))
        assertEquals(4, plan.preview.losses.attachments)
    }

    @Test
    fun mediaInATarIsDiscardedWithoutBeingInflatedOrKept() {
        val limits = ImportLimits(maxTotalBytes = 64L * 1024, maxSkippedBytes = 1024)
        val result = read(
            source("p.tar" to tar(TarFile("a.png", ByteArray(2 * 1024 * 1024)), TarFile("n.md", "x".toByteArray()))),
            limits,
        )
        assertEquals(listOf("n.md"), result.entries.map { it.path })
        assertEquals(1, result.attachments)
    }

    @Test
    fun hiddenAndUnsafeMediaAreNotAttachments() {
        val result = read(
            source(
                "p.zip" to zip(
                    "__MACOSX/._p.jpg" to ByteArray(1),
                    ".obsidian/x.png" to ByteArray(1),
                    "../evil.png" to ByteArray(1),
                    "ok.png" to ByteArray(1),
                ),
            ),
        )
        assertEquals(1, result.attachments)
        assertEquals(1, result.skips.unsafePaths)
    }

    @Test
    fun aPickedMediaFileIsNeverOpened() {
        val source = ImportFileSource {
            sequenceOf(
                SourceFile("Vault/pics/Photo.JPG") { throw AssertionError("a photo must not be opened") },
                SourceFile("doc.pdf") { throw AssertionError("a PDF must not be opened") },
                SourceFile("note.md") { Buffer().apply { write("hi".toByteArray()) } },
            )
        }
        val result = read(source)
        assertEquals(listOf("note.md"), result.entries.map { it.path })
        assertEquals(2, result.attachments)
    }

    @Test
    fun aWalkedFolderNeverOpensMediaAndCountsWhatIsTooDeep() {
        val opened = ArrayList<String>()
        val kids = HashMap<String, List<TreeNode>>()
        kids["root"] = listOf(TreeNode("photo", "photo.jpg", false), TreeNode("d1", "d1", true))
        for (i in 1..ImportLimits.MAX_PATH_DEPTH) {
            kids["d$i"] = listOf(TreeNode("n$i", "n$i.md", false), TreeNode("d${i + 1}", "d${i + 1}", true))
        }
        val tree = object : DocumentTree {
            override val rootId = "root"

            override fun children(parentId: String) = kids[parentId].orEmpty()

            override fun open(id: String): Buffer {
                opened += id
                return Buffer().apply { write(id.toByteArray()) }
            }
        }
        val result = ImportReader().read(TreeWalker(tree).source())

        // n1 is at depth 2 ... n15 at depth 16; n16 and the folder d17 are 17 deep.
        assertEquals((1..ImportLimits.MAX_PATH_DEPTH - 1).map { "n$it" }, opened)
        assertEquals(ImportLimits.MAX_PATH_DEPTH - 1, result.entries.size)
        assertEquals(1, result.attachments)
        assertEquals(2, result.skips.unsafePaths)
    }

    @Test
    fun everyTarHeaderCountsAgainstTheEntryLimit() {
        val limits = ImportLimits(maxEntries = 5)
        val dirs = (1..6).map { TarFile("d$it/", type = '5') }.toTypedArray()
        val error = assertThrows(ImportException::class.java) { read(source("p.tar" to tar(*dirs)), limits) }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    private fun directoryZip(count: Int, bytesEach: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            repeat(count) {
                zip.putNextEntry(java.util.zip.ZipEntry("d$it/"))
                if (bytesEach > 0) zip.write(ByteArray(bytesEach))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
