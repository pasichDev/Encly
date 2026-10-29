package com.pasich.encly.data.importer

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import kotlinx.io.readByteArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Android side of the import's file access. The import logic knows no `SecurityException`:
 * a provider that refuses a file must reach it as an unreadable one (`open` returns null), the
 * same as a file that is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SafImportFilesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    class DenyingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
        override fun getType(u: Uri): String? = null
        override fun insert(u: Uri, v: ContentValues?): Uri? = null
        override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
        override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

        override fun openFile(u: Uri, mode: String): ParcelFileDescriptor {
            if (u.lastPathSegment == "denied") throw SecurityException("not granted")
            val file = File.createTempFile("saf", null).apply { writeBytes("hello".toByteArray()) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    private fun open(name: String): kotlinx.io.RawSource? {
        Robolectric.buildContentProvider(DenyingProvider::class.java).create(AUTHORITY)
        val uri = Uri.parse("content://$AUTHORITY/$name")
        return SafImportFiles(context).files(listOf(uri)).files().single().open()
    }

    @Test
    fun aRefusedFileIsUnreadableNotAnException() {
        assertNull(open("denied"))
    }

    @Test
    fun aGrantedFileIsReadThroughTheStream() {
        val source = open("granted")

        assertNotNull(source)
        val buffer = kotlinx.io.Buffer()
        source!!.use { it.readAtMostTo(buffer, Long.MAX_VALUE) }
        assertEquals("hello", buffer.readByteArray().decodeToString())
    }

    private companion object {
        const val AUTHORITY = "com.pasich.encly.test.saf"
    }
}
