package com.pasich.encly.platform

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Android side of the document access: [AndroidDocuments] over a content provider. The logic above it
 * (backup, export, import) knows no `SecurityException`: a provider that refuses a document reaches it as an
 * [IOException], and a missing stream as null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidDocumentsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val documents get() = AndroidDocuments(context)

    class TestProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = when {
            u.lastPathSegment == "unnamed" -> null

            u.lastPathSegment == "refused-name" -> throw SecurityException("not granted")

            p?.contains(DocumentsContract.Document.COLUMN_DOCUMENT_ID) == true -> MatrixCursor(p).apply {
                addRow(arrayOf("a", "a.md", "text/markdown"))
                addRow(arrayOf("dir", "folder", DocumentsContract.Document.MIME_TYPE_DIR))
                addRow(arrayOf("c", "c.md", "text/markdown"))
            }

            p?.contains(OpenableColumns.DISPLAY_NAME) == true -> MatrixCursor(p).apply { addRow(arrayOf("Plan.md")) }

            else -> null
        }

        override fun getType(u: Uri): String? = null
        override fun insert(u: Uri, v: ContentValues?): Uri? = null
        override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
        override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

        override fun openFile(u: Uri, mode: String): ParcelFileDescriptor {
            if (u.toString().contains("denied")) throw SecurityException("not granted")
            if (u.toString().contains("missing")) throw java.io.FileNotFoundException("gone")
            val file = if (mode.startsWith("w")) {
                written.also { it.writeBytes(ByteArray(0)) }
            } else {
                File.createTempFile("doc", null).apply { writeBytes("hello".toByteArray()) }
            }
            val flags = if (mode.startsWith("w")) {
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
            } else {
                ParcelFileDescriptor.MODE_READ_ONLY
            }
            return ParcelFileDescriptor.open(file, flags)
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            calls += method
            return Bundle()
        }
    }

    private fun provider() {
        calls.clear()
        Robolectric.buildContentProvider(TestProvider::class.java).create(AUTHORITY)
    }

    private fun ref(name: String) = DocumentRef("content://$AUTHORITY/$name")

    private fun RawSource.text(): String = Buffer().also { buffer -> use { it.readAtMostTo(buffer, Long.MAX_VALUE) } }
        .readByteArray().decodeToString()

    @Test
    fun aGrantedDocumentIsReadThroughTheStream() {
        provider()

        assertEquals("hello", checkNotNull(documents.openRead(ref("granted"))).text())
    }

    @Test
    fun aRefusedDocumentIsAnIoErrorWithoutTheProvidersMessage() {
        provider()

        val failure = assertThrows(IOException::class.java) { documents.openRead(ref("denied")) }
        assertTrue(failure.message.orEmpty().none { it == '/' })
        assertThrows(IOException::class.java) { documents.openWrite(ref("denied")) }
    }

    @Test
    fun aDocumentWrittenToIsReplacedByWhatWasWritten() {
        provider()

        documents.openWrite(ref("granted"))!!.let { sink ->
            val buffer = Buffer().apply { writeString("saved") }
            sink.write(buffer, buffer.size)
            sink.close()
        }

        assertEquals("saved", written.readText())
    }

    @Test
    fun theDisplayNameIsTheProvidersAndNullWhenItHasNoneOrRefuses() {
        provider()

        assertEquals("Plan.md", documents.displayName(ref("named")))
        assertNull(documents.displayName(ref("unnamed")))
        assertNull(documents.displayName(ref("refused-name")))
    }

    @Test
    fun deletingAsksTheProviderToDeleteTheDocument() {
        provider()

        documents.delete(DocumentRef(DocumentsContract.buildDocumentUri(AUTHORITY, "doc").toString()))

        assertEquals(listOf("android:deleteDocument"), calls)
    }

    @Test
    fun aFolderIsListedAndItsFilesOpenedByTheirIds() {
        provider()
        val tree = documents.openTree(
            DocumentRef(DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root").toString()),
            2,
        )

        val children = tree.children(tree.rootId)

        // Capped at the entry limit.
        assertEquals(listOf("a.md", "folder"), children.map { it.name })
        assertEquals(listOf(false, true), children.map { it.isDirectory })
        assertEquals("hello", checkNotNull(tree.open("a")).text())
    }

    private companion object {
        const val AUTHORITY = "com.pasich.encly.test.documents"
        val calls = mutableListOf<String>()
        val written: File = File.createTempFile("written", null)

        fun File.readText() = readBytes().decodeToString()
    }
}
