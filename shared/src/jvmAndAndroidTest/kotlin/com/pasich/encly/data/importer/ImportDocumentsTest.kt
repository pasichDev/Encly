package com.pasich.encly.data.importer

import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.testutil.FakeDocuments
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The import's file access: the picked files are named by the provider (or "file"), and a file the provider
 * refuses reaches the import as one that cannot be read, never as another kind of failure.
 */
class ImportDocumentsTest {
    private val fake = FakeDocuments()
    private val documents = ImportDocuments(fake)
    private val note = DocumentRef("content://test/note")

    private fun RawSource.text(): String = Buffer().also { buffer -> use { it.readAtMostTo(buffer, Long.MAX_VALUE) } }
        .readByteArray().decodeToString()

    @Test
    fun aGrantedFileIsReadThroughTheStream() {
        fake.files[note] = "hello".toByteArray()

        val source = documents.files(listOf(note)).files().single().open()

        assertEquals("hello", checkNotNull(source).text())
    }

    @Test
    fun aFileWithoutAMissingStreamIsUnreadable() {
        fake.noStream = true

        assertNull(documents.files(listOf(note)).files().single().open())
    }

    @Test
    fun aRefusedFileIsAnIoErrorTheReaderCountsAsUnreadable() {
        fake.readFailure = IOException("access refused")

        assertThrows(IOException::class.java) { documents.files(listOf(note)).files().single().open() }
    }

    @Test
    fun theFilesAreNamedByTheProviderOrFile() {
        val other = DocumentRef("content://test/other")
        fake.names[note] = "Plan.md"

        val names = documents.files(listOf(note, other)).files().map { it.path }.toList()

        assertEquals(listOf("Plan.md", "file"), names)
    }

    @Test
    fun aNameTheProviderRefusesToGiveIsTheFallback() {
        val broken = object : com.pasich.encly.platform.PlatformDocuments by fake {
            override fun displayName(doc: DocumentRef): String? = throw IllegalArgumentException("Unknown URI")
        }

        assertEquals("file", ImportDocuments(broken).files(listOf(note)).files().single().path)
    }

    @Test
    fun aFolderIsWalkedThroughItsTree() {
        val folder = DocumentRef("content://test/folder")
        fake.trees[folder] = object : DocumentTree {
            override val rootId = "root"

            override fun children(parentId: String) = listOf(TreeNode("a", "a.md", isDirectory = false))

            override fun open(id: String): RawSource? = Buffer().apply { write("# A".toByteArray()) }
        }

        val file = documents.tree(folder).files().single()

        assertEquals("a.md", file.path)
        assertEquals("# A", checkNotNull(file.open()).text())
    }
}
