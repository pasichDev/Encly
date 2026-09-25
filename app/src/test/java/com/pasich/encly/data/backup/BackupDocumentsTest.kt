package com.pasich.encly.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.backup.assertError
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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

        assertArrayEquals(backup, BackupDocuments.readCapped(input, limit = backup.size))
    }

    @Test
    fun rejectsAFileDeclaringMoreThanTheLimitWithoutReadingIt() {
        val input = CountingInputStream(backup)

        assertError(BackupError.TOO_LARGE) { BackupDocuments.readCapped(input, limit = backup.size - 1) }
        assertEquals(BackupFormat.HEADER_LENGTH, input.consumed)
    }

    @Test
    fun rejectsAnUnsignedLengthAboveIntMax() {
        val header = backup.copyOf(BackupFormat.HEADER_LENGTH)
        ByteBuffer.wrap(header).putInt(LENGTH_OFFSET, -1) // 0xFFFFFFFF

        assertError(BackupError.TOO_LARGE) { BackupDocuments.readCapped(ByteArrayInputStream(header)) }
    }

    @Test
    fun rejectsDataPastTheDeclaredEnd() {
        assertError(BackupError.CORRUPTED) {
            BackupDocuments.readCapped(ByteArrayInputStream(backup + byteArrayOf(0)))
        }
    }

    @Test
    fun aShortFileIsLeftForParseToReportTruncated() {
        val short = BackupDocuments.readCapped(ByteArrayInputStream(backup.copyOf(backup.size - 1)))

        assertEquals(backup.size - 1, short.size)
        assertError(BackupError.TRUNCATED) { BackupFormat.parse(short) }
    }

    @Test
    fun otherFilesAreRejectedAfterTheirFirstBytes() {
        val input = CountingInputStream(ByteArray(1_000_000))

        assertError(BackupError.NOT_A_BACKUP) { BackupDocuments.readCapped(input) }
        assertEquals(BackupFormat.HEADER_LENGTH, input.consumed)
    }

    // --- writing --------------------------------------------------------------------------

    private lateinit var resolver: ContentResolver
    private lateinit var documents: BackupDocuments
    private val uri = mock(Uri::class.java)
    private var stored = ByteArray(0)

    @Before
    fun setUp() {
        resolver = mock(ContentResolver::class.java)
        val context = mock(Context::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        documents = BackupDocuments(context)
        `when`(resolver.openInputStream(uri)).thenAnswer { ByteArrayInputStream(stored) }
        `when`(resolver.openOutputStream(uri, "wt")).thenAnswer { StoringOutputStream() }
    }

    @Test
    fun writesToANewDocumentAndChecksItBack() {
        documents.write(uri, backup)

        assertArrayEquals(backup, stored)
    }

    @Test
    fun neverOverwritesAnExistingBackup() {
        val previous = backup.copyOf()
        stored = previous

        assertError(BackupError.TARGET_EXISTS) { documents.write(uri, byteArrayOf(1, 2, 3)) }

        verify(resolver, never()).openOutputStream(uri, "wt")
        assertArrayEquals(previous, stored)
    }

    @Test
    fun aWriteThatDoesNotReadBackIsAnError() {
        `when`(resolver.openOutputStream(uri, "wt")).thenAnswer { TruncatingOutputStream() }

        assertError(BackupError.IO) { documents.write(uri, backup) }
    }

    @Test
    fun aFailedWriteIsAnIoError() {
        `when`(resolver.openOutputStream(uri, "wt")).thenAnswer { throw IOException("No space left") }

        assertError(BackupError.IO) { documents.write(uri, backup) }
    }

    @Test
    fun providerRuntimeExceptionsBecomeIoErrorsNotCrashes() {
        listOf(
            IllegalArgumentException("Unknown URI"),
            IllegalStateException("provider died"),
            UnsupportedOperationException("mode wt"),
            NullPointerException(),
        ).forEach { failure ->
            doThrow(failure).`when`(resolver).openInputStream(uri)

            assertError(BackupError.IO) { documents.read(uri) }
            assertError(BackupError.IO) { documents.write(uri, backup) }
        }
    }

    @Test
    fun aMissingStreamIsAnIoError() {
        `when`(resolver.openInputStream(uri)).thenReturn(null)

        assertError(BackupError.IO) { documents.read(uri) }
    }

    private inner class StoringOutputStream : ByteArrayOutputStream() {
        override fun close() {
            super.close()
            stored = toByteArray()
        }
    }

    /** A provider that silently loses the last byte. */
    private inner class TruncatingOutputStream : ByteArrayOutputStream() {
        override fun close() {
            super.close()
            stored = toByteArray().copyOf(size() - 1)
        }
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
