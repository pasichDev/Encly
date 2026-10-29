package com.pasich.encly.desktop.platform

import com.pasich.encly.data.importer.DocumentTree
import com.pasich.encly.data.importer.TreeNode
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/** The [DocumentRef] of a picked file or folder: the desktop names a document by its absolute path. */
fun Path.toDocumentRef(): DocumentRef = DocumentRef(toAbsolutePath().normalize().toString())

/**
 * [PlatformDocuments] over the file system: the documents the user picked in a native dialog or dropped on the
 * window, reached by their path. Reading a missing or unreadable file is an [IOException], like a refused
 * grant on Android. Folders are walked without following symbolic links, so a link inside a picked folder
 * can never lead the import to a file outside it.
 */
class DesktopDocuments : PlatformDocuments {
    override fun openRead(doc: DocumentRef): RawSource? = path(doc)?.let { Files.newInputStream(it).asSource() }

    // Replaces the content, as Android's "wt" mode does; creates the file if the dialog did not.
    override fun openWrite(doc: DocumentRef): RawSink? = path(doc)?.let {
        Files.newOutputStream(
            it,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).asSink()
    }

    override fun displayName(doc: DocumentRef): String? = path(doc)?.fileName?.toString()

    override fun delete(doc: DocumentRef) {
        path(doc)?.let(Files::deleteIfExists)
    }

    override fun openTree(root: DocumentRef, maxEntries: Int): DocumentTree {
        val base = path(root) ?: throw IOException("not a folder")
        return FolderTree(base, maxEntries)
    }

    private fun path(doc: DocumentRef): Path? = try {
        Paths.get(doc.value)
    } catch (_: InvalidPathException) {
        null
    }

    private class FolderTree(private val root: Path, private val maxEntries: Int) : DocumentTree {
        override val rootId: String = root.toString()

        override fun children(parentId: String): List<TreeNode> {
            val parent = Paths.get(parentId)
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) return emptyList()
            return try {
                Files.newDirectoryStream(parent).use { entries ->
                    entries.asSequence()
                        .filter { !Files.isSymbolicLink(it) }
                        .take(maxEntries)
                        .map { TreeNode(it.toString(), it.fileName.toString(), Files.isDirectory(it)) }
                        .toList()
                }
            } catch (_: IOException) {
                emptyList()
            }
        }

        override fun open(id: String): RawSource? {
            val file = Paths.get(id)
            val inside = file.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())
            if (!inside || Files.isSymbolicLink(file) || !Files.isRegularFile(file)) return null
            return try {
                Files.newInputStream(file).asSource()
            } catch (_: IOException) {
                null
            }
        }
    }
}
