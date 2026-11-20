package com.pasich.encly.desktop

import com.pasich.encly.desktop.nativeos.NativeBackupExclusion
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.storage.TimeMachineExclusion
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupExclusionTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the attribute value is the binary plist of the string com apple backupd`() {
        val value = TimeMachineExclusion.attributeValue()

        // "bplist00", the string object (0x5F, 1-byte int 17, 17 characters), one offset, the 32-byte trailer.
        assertEquals(8 + 3 + 17 + 1 + 32, value.size)
        assertContentEquals("bplist00".toByteArray(), value.copyOfRange(0, 8))
        assertEquals(0x5F.toByte(), value[8])
        assertEquals(0x10.toByte(), value[9])
        assertEquals(17.toByte(), value[10])
        assertEquals("com.apple.backupd", String(value, 11, 17, Charsets.US_ASCII))
        // The offset table has the one object at offset 8 and sits right after the object.
        assertEquals(8.toByte(), value[28])
        val trailer = value.copyOfRange(29, value.size)
        assertContentEquals(ByteArray(6), trailer.copyOfRange(0, 6))
        assertEquals(1.toByte(), trailer[6])
        assertEquals(1.toByte(), trailer[7])
        assertEquals(1L, trailer.copyOfRange(8, 16).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) })
        assertEquals(0L, trailer.copyOfRange(16, 24).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) })
        assertEquals(28L, trailer.copyOfRange(24, 32).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) })
    }

    @Test
    fun `other systems have nothing to exclude and say so`() {
        assertTrue(NativeBackupExclusion.forOs(DesktopOs.WINDOWS).exclude(temp.root.toPath()))
        assertTrue(NativeBackupExclusion.forOs(DesktopOs.LINUX).exclude(temp.root.toPath()))
    }

    @Test
    fun `time machine really leaves the directory out`() {
        assumeTrue("ENCLY_NATIVE_IT is not set", System.getenv("ENCLY_NATIVE_IT") == "1")
        assumeTrue(System.getProperty("os.name").lowercase().startsWith("mac"))
        val directory = temp.newFolder("encly-data").toPath()

        assertTrue(NativeBackupExclusion.forOs(DesktopOs.MACOS).exclude(directory))

        val answer = ProcessBuilder("tmutil", "isexcluded", directory.toString())
            .redirectErrorStream(true)
            .start()
            .let { process -> process.inputStream.bufferedReader().readText().also { process.waitFor() } }
        assertTrue("[Excluded]" in answer, "tmutil said: $answer")
    }
}
