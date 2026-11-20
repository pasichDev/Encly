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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

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

    // Replaces the content, as Android's "wt" mode does; creates the file if the dialog did not. A file made here is
    // for its owner only (a plaintext export must not be world-readable under a permissive umask); one that
    // already exists keeps the permissions it has.
    override fun openWrite(doc: DocumentRef): RawSink? = path(doc)?.let {
        createOwnerOnlyIfMissing(it)
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

    /**
     * Creates [target] with `rw-------` where the file system has POSIX permissions (macOS, Linux). Anywhere else,
     * or when [target] is there already, nothing happens and the write below behaves as before.
     */
    private fun createOwnerOnlyIfMissing(target: Path) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return
        try {
            val parent = target.toAbsolutePath().parent
            if (parent != null && Files.getFileStore(parent).supportsFileAttributeView("posix")) {
                Files.createFile(target, PosixFilePermissions.asFileAttribute(OWNER_ONLY))
            }
        } catch (_: FileAlreadyExistsException) {
            // Made by someone else in the meantime: it is written to like any existing file.
        } catch (_: IOException) {
            // No such folder or no right to create here: the write below fails with the proper error.
        }
    }

    private fun path(doc: DocumentRef): Path? = try {
        Paths.get(doc.value)
    } catch (_: InvalidPathException) {
        null
    }

    private companion object {
        val OWNER_ONLY: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")
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
