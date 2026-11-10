package com.pasich.encly.data.export

import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.assertError
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.testutil.FakeDocuments
import kotlinx.io.IOException
import kotlinx.io.writeString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A failed export removes the empty document the save dialog created, and nothing else: a
 * document that holds data (an existing file a provider handed back) is never deleted.
 */
class MarkdownDocumentsTest {
    private val fake = FakeDocuments()
    private val documents = MarkdownDocuments(fake)
    private val doc = DocumentRef("content://test/note.md")

    @Test
    fun anEmptyDocumentIsDeleted() {
        documents.discard(doc)

        assertEquals(listOf(doc), fake.deleted)
    }

    @Test
    fun aDocumentWithDataIsNeverDeleted() {
        fake.files[doc] = "keep me".toByteArray()

        documents.discard(doc)

        assertEquals(emptyList<DocumentRef>(), fake.deleted)
    }

    @Test
    fun anUnreadableDocumentIsLeftAlone() {
        fake.readFailure = SecurityException("grant revoked")

        documents.discard(doc)

        assertEquals(emptyList<DocumentRef>(), fake.deleted)
    }

    @Test
    fun streamsTheContentIntoTheDocument() {
        documents.write(doc) { it.writeString("# Title") }

        assertEquals("# Title", fake.files.getValue(doc).decodeToString())
        assertEquals(emptyList<DocumentRef>(), fake.deleted)
    }

    @Test
    fun neverOverwritesADocumentThatHoldsData() {
        fake.files[doc] = "old".toByteArray()

        assertError(BackupError.TARGET_EXISTS) { documents.write(doc) { it.writeString("new") } }

        assertEquals("old", fake.files.getValue(doc).decodeToString())
        assertEquals(0, fake.opensForWrite)
    }

    @Test
    fun aFailureWhileWritingDeletesTheIncompleteDocument() {
        assertError(BackupError.IO) { documents.write(doc) { throw IOException("No space left") } }

        assertEquals(listOf(doc), fake.deleted)
    }

    @Test
    fun providerFailuresBecomeIoErrorsWithoutTheirMessage() {
        listOf(SecurityException("grant revoked at /secret/path"), IllegalStateException("provider died")).forEach {
            fake.writeFailure = it

            val failure = runCatching { documents.write(doc) { sink -> sink.writeString("x") } }.exceptionOrNull()

            assertEquals(BackupError.IO, (failure as com.pasich.encly.core.backup.BackupException).error)
            assertEquals(BackupError.IO.name, failure.message)
        }
    }

    @Test
    fun aProviderWithNoStreamIsAnIoError() {
        val noWrite = object : com.pasich.encly.platform.PlatformDocuments by fake {
            override fun openWrite(doc: DocumentRef) = null
        }

        assertError(BackupError.IO) { MarkdownDocuments(noWrite).write(doc) { it.writeString("x") } }
    }
}
