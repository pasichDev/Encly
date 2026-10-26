package com.pasich.encly.data.export

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A failed export removes the empty document the save dialog created, and nothing else: a
 * document that holds data (an existing file a provider handed back) is never deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownDocumentsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val uri: Uri = DocumentsContract.buildDocumentUri(AUTHORITY, "doc")

    class RecordingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
        override fun getType(u: Uri): String? = null
        override fun insert(u: Uri, v: ContentValues?): Uri? = null
        override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
        override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

        override fun openFile(u: Uri, mode: String): ParcelFileDescriptor {
            val file = File.createTempFile("doc", null).apply { writeBytes(content) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            calls += method
            return Bundle()
        }
    }

    private fun provider(documentContent: ByteArray) {
        calls.clear()
        content = documentContent
        Robolectric.buildContentProvider(RecordingProvider::class.java).create(AUTHORITY)
    }

    @Test
    fun anEmptyDocumentIsDeleted() {
        provider(ByteArray(0))

        MarkdownDocuments(context).discard(uri)

        assertEquals(listOf("android:deleteDocument"), calls)
    }

    @Test
    fun aDocumentWithDataIsNeverDeleted() {
        provider("keep me".toByteArray())

        MarkdownDocuments(context).discard(uri)

        assertEquals(emptyList<String>(), calls)
    }

    private companion object {
        const val AUTHORITY = "com.pasich.encly.test.documents"
        val calls = mutableListOf<String>()
        var content = ByteArray(0)
    }
}
