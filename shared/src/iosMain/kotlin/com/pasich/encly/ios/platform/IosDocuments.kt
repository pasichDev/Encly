@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.platform

import com.pasich.encly.data.importer.DocumentTree
import com.pasich.encly.data.importer.TreeNode
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.lstat
import platform.posix.stat

/**
 * [PlatformDocuments] over the file system. A document is named by its path: the system's document picker either
 * copies what the user picks into the app (a file to open), gives the app a destination it may write (a file to
 * save) or lets it read a folder in place (the access of a picked folder is held by [SecurityScopes] until released).
 * Reading a missing or unreadable file is an [IOException], like a refused grant on Android. Folders are walked
 * without following symbolic links, so a link inside a picked folder can never lead the import to a file outside it.
 */
internal class IosDocuments : PlatformDocuments {
    override fun openRead(doc: DocumentRef): RawSource = SystemFileSystem.source(Path(doc.value))

    // Replaces the content, as Android's "wt" mode does; creates the file if the picker did not.
    override fun openWrite(doc: DocumentRef): RawSink = SystemFileSystem.sink(Path(doc.value), append = false)

    override fun displayName(doc: DocumentRef): String? = doc.value.substringAfterLast('/').takeIf { it.isNotEmpty() }

    override fun delete(doc: DocumentRef) {
        runCatching { SystemFileSystem.delete(Path(doc.value), mustExist = false) }
    }

    override fun openTree(root: DocumentRef, maxEntries: Int): DocumentTree {
        val base = runCatching { SystemFileSystem.resolve(Path(root.value)) }.getOrNull()
            ?: throw IOException("not a folder")
        return FolderTree(base, maxEntries)
    }

    private class FolderTree(private val root: Path, private val maxEntries: Int) : DocumentTree {
        override val rootId: String = root.toString()

        override fun children(parentId: String): List<TreeNode> {
            val parent = Path(parentId)
            if (isSymbolicLink(parentId) || SystemFileSystem.metadataOrNull(parent)?.isDirectory != true) {
                return emptyList()
            }
            return try {
                SystemFileSystem.list(parent)
                    .asSequence()
                    .filter { !isSymbolicLink(it.toString()) }
                    .take(maxEntries)
                    .map { TreeNode(it.toString(), it.name, SystemFileSystem.metadataOrNull(it)?.isDirectory == true) }
                    .toList()
            } catch (_: IOException) {
                emptyList()
            }
        }

        override fun open(id: String): RawSource? {
            val file = Path(id)
            val inside = runCatching { SystemFileSystem.resolve(file).toString() }.getOrNull()
                ?.startsWith(root.toString() + "/") == true
            if (!inside || isSymbolicLink(id) || SystemFileSystem.metadataOrNull(file)?.isRegularFile != true) {
                return null
            }
            return try {
                SystemFileSystem.source(file)
            } catch (_: IOException) {
                null
            }
        }
    }
}

/** True when [path] itself (not what it points to) is a symbolic link. */
private fun isSymbolicLink(path: String): Boolean = memScoped {
    val info = alloc<stat>()
    lstat(path, info.ptr) == 0 && (info.st_mode.toInt() and S_IFMT) == S_IFLNK
}

/**
 * The security-scoped access that the document picker grants to a folder or a destination file for as long as the
 * app holds it. [retain] starts it; [releaseAll] ends it, which the host does when the app leaves the screen, the
 * vault locks, or another picker is opened, so a grant never outlives the operation it was given for.
 */
internal class SecurityScopes {
    private val lock = SynchronizedObject()
    private val held = mutableListOf<NSURL>()

    fun retain(url: NSURL) {
        if (url.startAccessingSecurityScopedResource()) synchronized(lock) { held += url }
    }

    fun releaseAll() {
        val urls = synchronized(lock) { held.toList().also { held.clear() } }
        urls.forEach { it.stopAccessingSecurityScopedResource() }
    }
}

/**
 * Copies the document picker made into the app (a file to import), kept in a folder of the cache that is deleted
 * as soon as the host says so (at start, when the vault locks and when another picker opens): an imported plaintext
 * file does not stay on the device because the user forgot it.
 */
internal class PickedCopies(private val directory: Path) {
    /** A fresh folder for one pick. */
    fun newFolder(): Path {
        val folder = Path(directory, randomName())
        SystemFileSystem.createDirectories(folder)
        return folder
    }

    /** Moves [source] (a file the system copied for the app) into [folder]; the new path. */
    @Suppress("ReturnCount") // Guards: a name and a path are needed.
    fun adopt(source: NSURL, folder: Path): String? {
        val name = source.lastPathComponent ?: return null
        val target = Path(folder, name).toString()
        val moved = NSFileManager.defaultManager.moveItemAtPath(source.path ?: return null, target, null)
        return if (moved) target else source.path
    }

    fun purge() {
        runCatching {
            if (SystemFileSystem.exists(directory)) {
                SystemFileSystem.list(directory).forEach { deleteTree(it) }
            }
        }
    }

    private fun deleteTree(path: Path) {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true && !isSymbolicLink(path.toString())) {
            SystemFileSystem.list(path).forEach(::deleteTree)
        }
        SystemFileSystem.delete(path, mustExist = false)
    }

    private fun randomName(): String = platform.Foundation.NSUUID().UUIDString
}
