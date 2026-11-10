@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the document picker's copies do when they cannot be moved into the folder the purge empties. */
class PickedCopiesTest {
    private val root = Path(NSTemporaryDirectory().trimEnd('/') + "/encly-picked-" + NSUUID().UUIDString)
    private val copies = PickedCopies(Path(root, "copies"))

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(root.toString(), null)
    }

    private fun write(path: Path) {
        SystemFileSystem.createDirectories(path.parent!!)
        SystemFileSystem.sink(path).buffered().use { it.writeString("secret") }
    }

    @Test
    fun aCopyIsMovedIntoThePurgeFolder() {
        val source = Path(root, "incoming/a.txt").also(::write)
        val folder = copies.newFolder()

        val adopted = assertNotNull(copies.adopt(source.toString(), "a.txt", folder))

        assertEquals(Path(folder, "a.txt").toString(), adopted)
        assertTrue(SystemFileSystem.exists(Path(adopted)))
        assertFalse(SystemFileSystem.exists(source))
        copies.purge()
        assertFalse(SystemFileSystem.exists(Path(adopted)))
    }

    @Test
    fun aCopyThatCannotBeMovedIsDeletedAndTheFileRefused() {
        val source = Path(root, "incoming/b.txt").also(::write)
        val missingFolder = Path(root, "copies/not-created")

        val adopted = copies.adopt(source.toString(), "b.txt", missingFolder)

        assertNull(adopted)
        assertFalse(SystemFileSystem.exists(source))
    }
}
