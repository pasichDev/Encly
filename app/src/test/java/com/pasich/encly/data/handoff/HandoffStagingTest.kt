package com.pasich.encly.data.handoff

import com.pasich.encly.data.handoff.HandoffFixtures.handoff
import com.pasich.encly.data.handoff.HandoffFixtures.handoffZip
import com.pasich.encly.data.handoff.HandoffFixtures.note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream

class HandoffStagingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val stagingDir: File get() = File(temp.root, HandoffStaging.DIR_NAME)

    @Test
    fun loadsAndLeavesNothingBehind() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))
        val loaded = HandoffStaging(stagingDir).load { zip.inputStream() }
        assertEquals(1, loaded.preview.notes)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun aFailedReadLeavesNothingBehind() {
        assertError(HandoffError.INVALID_PAYLOAD) {
            HandoffStaging(stagingDir).load { "not a zip".byteInputStream() }
        }
        assertFalse(stagingDir.exists())
        assertError(HandoffError.TOO_LARGE) {
            HandoffStaging(stagingDir, HandoffLimits(maxZipBytes = 8)).load { ByteArray(64).inputStream() }
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun anUnreadableUriLeavesNothingBehind() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("revoked")
        }
        try {
            HandoffStaging(stagingDir).load { failing }
        } catch (_: IOException) {
            // Expected: the ViewModel reports it as "failed".
        }
        assertFalse(stagingDir.exists())
        assertError(HandoffError.INVALID_PAYLOAD) { HandoffStaging(stagingDir).load { null } }
    }

    @Test
    fun leftoversOfAKilledRunAreSwept() {
        stagingDir.mkdirs()
        File(stagingDir, "handoff-123.zip").writeText("plaintext from a run that died")
        HandoffStaging.sweep(stagingDir)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun aSweepDuringALoadLeavesThatLoadsFileAlone() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))
        File(stagingDir.apply { mkdirs() }, "handoff-old.zip").writeText("left by a killed run")
        var during: List<String> = emptyList()

        val loaded = HandoffStaging(stagingDir).load {
            // Another hand-off, or the app's start, sweeps while this one is copying.
            HandoffStaging.sweep(stagingDir)
            during = stagingDir.list().orEmpty().toList()
            zip.inputStream()
        }

        assertEquals(1, loaded.preview.notes)
        assertEquals("only this load's own file is left", 1, during.size)
        assertTrue(during.single() != "handoff-old.zip")
        assertFalse(stagingDir.exists())
    }

    @Test
    fun twoLoadsAtOnceUseFilesOfTheirOwn() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))
        var inner: HandoffImport? = null
        var filesDuringInner = 0

        val outer = HandoffStaging(stagingDir).load {
            inner = HandoffStaging(stagingDir).load {
                filesDuringInner = stagingDir.list().orEmpty().size
                zip.inputStream()
            }
            // The inner load deleted its own file only, so this one still copies into its file.
            zip.inputStream()
        }

        assertEquals(2, filesDuringInner)
        assertEquals(1, outer.preview.notes)
        assertEquals(1, inner?.preview?.notes)
        assertFalse(stagingDir.exists())
    }
}
