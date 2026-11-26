package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.read
import com.pasich.encly.data.importer.ImportFixtures.tar
import com.pasich.encly.data.importer.ImportFixtures.zip
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The reader against what a real provider gives: a pipe with no size and no seek that hands out a few
 * bytes a time, can be opened once, and fails when the disk behind it does.
 */
class ImportReaderStreamsTest {

    /** One to three bytes per read, then [failure] once [failAfter] bytes were given (or the end). */
    private class Pipe(
        private val bytes: ByteArray,
        private val failAfter: Int = Int.MAX_VALUE,
        private val failure: () -> Throwable = { IOException("No space left on device") },
    ) : RawSource {
        private var position = 0
        private var reads = 0
        var closed = false
            private set

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            check(!closed) { "read after close" }
            if (position >= failAfter) throw failure()
            val limit = minOf(bytes.size, failAfter)
            if (position >= limit) return -1
            val n = minOf(1 + reads++ % 3, limit - position, byteCount.toInt())
            sink.write(bytes, position, position + n)
            position += n
            return n.toLong()
        }

        override fun close() {
            closed = true
        }
    }

    /** Every file can be opened once only: a second open is a test failure, as it is for a one-shot pipe. */
    private class OneShot(files: Map<String, ByteArray>) {
        val pipes = ArrayList<Pipe>()
        private val opened = HashSet<String>()
        val source = ImportFileSource {
            files.entries.asSequence().map { (path, bytes) ->
                SourceFile(path) {
                    check(opened.add(path)) { "$path was opened twice" }
                    Pipe(bytes).also(pipes::add)
                }
            }
        }
    }

    private val json = """{"version":"004","items":[""" +
        (1..120).joinToString(",") { n ->
            """{"uuid":"n$n","content_type":"Note","content":{"title":"T$n","text":"x"}}"""
        } + "]}"

    @Test
    fun plainZipAndTarFilesAreReadOnceFromAPipeThatTrickles() {
        val note = "# a note\n\nwith a body".repeat(40).toByteArray()
        val shot = OneShot(
            mapOf(
                "backup.json" to json.toByteArray(),
                "pack.zip" to zip("dir/a.md" to note, "dir/b.json" to json.toByteArray()),
                "pack.tar" to tar(TarFile("c.md", note), TarFile("sub/d.json", json.toByteArray())),
            ),
        )

        val result = read(shot.source)

        assertEquals(
            listOf("backup.json", "dir/a.md", "dir/b.json", "c.md", "sub/d.json"),
            result.entries.map { it.path },
        )
        assertEquals(
            listOf(json.length, note.size, json.length, note.size, json.length),
            result.entries.map { it.bytes.size },
        )
        assertEquals(0, result.skips.total)
        assertTrue("every pipe was closed", shot.pipes.all { it.closed })
    }

    @Test
    fun theLimitsStillHoldOverAPipe() {
        val limits = ImportLimits(maxEntryBytes = 1024, maxContainerBytes = 4096)
        val big = ByteArray(5000) { 'a'.code.toByte() }
        val shot = OneShot(mapOf("p.zip" to zip("big.json" to big, "ok.json" to ByteArray(100) { ' '.code.toByte() })))

        val result = ImportReader(limits).read(shot.source)

        // The oversized container is dropped and counted, and the entry after it is still found.
        assertEquals(listOf("ok.json"), result.entries.map { it.path })
        assertEquals(1, result.skips.tooLarge)
    }

    @Test
    fun aDiskThatFillsUpMidFileCountsTheFileAsUnreadableAndTheRestStillReads() {
        val files = ImportFileSource {
            sequenceOf(
                SourceFile("full.md") { Pipe("some text".repeat(100).toByteArray(), failAfter = 50) },
                SourceFile("ok.md") { Pipe("ok".toByteArray()) },
            )
        }

        val result = read(files)

        assertEquals(listOf("ok.md"), result.entries.map { it.path })
        assertEquals(1, result.skips.unreadable)
    }

    @Test
    fun aDiskThatFillsUpMidArchiveKeepsWhatWasReadAndCountsTheRest() {
        val noise = java.util.Random(7).let { r -> ByteArray(20_000).also(r::nextBytes) }
        val good = zip("a.md" to "A".toByteArray(), "b.md" to noise)
        val pipes = ArrayList<Pipe>()
        val files = ImportFileSource {
            sequenceOf(SourceFile("p.zip") { Pipe(good, failAfter = good.size / 2).also(pipes::add) })
        }

        val result = read(files)

        assertEquals(listOf("a.md"), result.entries.map { it.path })
        assertEquals(1, result.skips.unreadable)
        assertTrue(pipes.single().closed)
    }

    @Test
    fun outOfMemoryWhileReadingIsNotSwallowedAndTheStreamIsClosed() {
        val pipes = ArrayList<Pipe>()
        val files = ImportFileSource {
            sequenceOf(
                SourceFile("big.md") {
                    Pipe("text".repeat(100).toByteArray(), failAfter = 40) { OutOfMemoryError("simulated") }
                        .also(pipes::add)
                },
                SourceFile("never.md") { throw AssertionError("the read goes on after an out-of-memory error") },
            )
        }

        assertThrows(OutOfMemoryError::class.java) { read(files) }
        assertTrue(pipes.single().closed)
    }

    @Test
    fun aProviderThatReturnsNoStreamOrFailsToOpenIsUnreadableNotFatal() {
        val files = ImportFileSource {
            sequenceOf(
                SourceFile("none.md") { null },
                SourceFile("denied.md") { throw IOException("permission denied") },
                SourceFile("ok.md") { Pipe("ok".toByteArray()) },
            )
        }
        val result = read(files)
        assertEquals(listOf("ok.md"), result.entries.map { it.path })
        assertEquals(2, result.skips.unreadable)
    }
}
