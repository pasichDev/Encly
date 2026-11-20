package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.backup.assertError
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import com.pasich.encly.testutil.FakeDocuments
import kotlinx.io.asSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class BackupDocumentsTest {

    private val backup = BackupCipher.seal(ByteArray(200_000) { it.toByte() }, BackupSecret.RecoveryRoot(ByteArray(32)))

    // --- reading --------------------------------------------------------------------------

    @Test
    fun readsABackupSizedFromItsHeader() {
        val input = CountingInputStream(backup)

        assertArrayEquals(backup, BackupDocuments.readCapped(input.asSource(), limit = backup.size))
    }

    @Test
    fun rejectsAFileDeclaringMoreThanTheLimitWithoutReadingIt() {
        val input = CountingInputStream(backup)

        assertError(BackupError.TOO_LARGE) { BackupDocuments.readCapped(input.asSource(), limit = backup.size - 1) }
        assertEquals(BackupFormat.HEADER_LENGTH, input.consumed)
    }

    @Test
    fun rejectsAnUnsignedLengthAboveIntMax() {
        val header = backup.copyOf(BackupFormat.HEADER_LENGTH)
        ByteBuffer.wrap(header).putInt(LENGTH_OFFSET, -1) // 0xFFFFFFFF

        assertError(BackupError.TOO_LARGE) { BackupDocuments.readCapped(ByteArrayInputStream(header).asSource()) }
    }

    @Test
    fun rejectsDataPastTheDeclaredEnd() {
        assertError(BackupError.CORRUPTED) {
            BackupDocuments.readCapped(ByteArrayInputStream(backup + byteArrayOf(0)).asSource())
        }
    }

    @Test
    fun aShortFileIsLeftForParseToReportTruncated() {
        val short = BackupDocuments.readCapped(ByteArrayInputStream(backup.copyOf(backup.size - 1)).asSource())

        assertEquals(backup.size - 1, short.size)
        assertError(BackupError.TRUNCATED) { BackupFormat.parse(short) }
    }

    @Test
    fun otherFilesAreRejectedAfterTheirFirstBytes() {
        val input = CountingInputStream(ByteArray(1_000_000))

        assertError(BackupError.NOT_A_BACKUP) { BackupDocuments.readCapped(input.asSource()) }
        assertEquals(BackupFormat.HEADER_LENGTH, input.consumed)
    }

    // --- writing --------------------------------------------------------------------------

    private val fake = FakeDocuments()
    private val documents = BackupDocuments(fake)
    private val doc = DocumentRef("content://test/backup.enclybak")

    @Test
    fun writesToANewDocumentAndChecksItBack() {
        documents.write(doc, backup)

        assertArrayEquals(backup, fake.files[doc])
        assertEquals(emptyList<DocumentRef>(), fake.deleted)
    }

    @Test
    fun neverOverwritesAnExistingBackup() {
        val previous = backup.copyOf()
        fake.files[doc] = previous

        assertError(BackupError.TARGET_EXISTS) { documents.write(doc, byteArrayOf(1, 2, 3)) }

        assertEquals(0, fake.opensForWrite)
        assertArrayEquals(previous, fake.files[doc])
    }

    @Test
    fun aWriteThatDoesNotReadBackIsAnErrorAndLeavesNoDocument() {
        fake.losesLastByte = true

        assertError(BackupError.IO) { documents.write(doc, backup) }

        assertEquals(listOf(doc), fake.deleted)
    }

    @Test
    fun aFailedWriteIsAnIoError() {
        fake.writeFailure = IOException("No space left")

        assertError(BackupError.IO) { documents.write(doc, backup) }
    }

    @Test
    fun aProviderThatCannotDeleteDoesNotHideTheWriteError() {
        fake.losesLastByte = true
        val stubborn = object : PlatformDocuments by fake {
            override fun delete(doc: DocumentRef) = throw UnsupportedOperationException("no delete")
        }

        assertError(BackupError.IO) { BackupDocuments(stubborn).write(doc, backup) }
    }

    @Test
    fun providerRuntimeExceptionsBecomeIoErrorsNotCrashes() {
        listOf(
            IllegalArgumentException("Unknown URI"),
            IllegalStateException("provider died"),
            UnsupportedOperationException("mode wt"),
            NullPointerException(),
            SecurityException("grant revoked"),
        ).forEach { failure ->
            fake.readFailure = failure

            assertError(BackupError.IO) { documents.read(doc) }
            assertError(BackupError.IO) { documents.write(doc, backup) }
        }
    }

    @Test
    fun aMissingStreamIsAnIoError() {
        fake.noStream = true

        assertError(BackupError.IO) { documents.read(doc) }
    }

    @Test
    fun readsTheDocumentThroughTheCap() {
        fake.files[doc] = backup

        assertArrayEquals(backup, documents.read(doc))
    }

    @Test
    fun theDisplayNameIsWhatTheProviderSaysAndNullWhenItFails() {
        fake.names[doc] = "backup.enclybak"
        assertEquals("backup.enclybak", documents.displayName(doc))

        fake.readFailure = IllegalStateException("provider died")
        assertEquals(null, documents.displayName(doc))
    }

    private class CountingInputStream(bytes: ByteArray) : InputStream() {
        private val input = ByteArrayInputStream(bytes)
        var consumed = 0
            private set

        override fun read(): Int = input.read().also { if (it >= 0) consumed++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len).also {
            if (it >
                0
            ) {
                consumed += it
            }
        }
    }

    private companion object {
        const val LENGTH_OFFSET = 57
    }
}
