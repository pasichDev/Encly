package com.pasich.encly.desktop

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleInstanceLockTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a second start in the same data directory is refused until the first ends`() {
        val dir = temp.root.toPath()
        val first = SingleInstanceLock.tryAcquire(dir)
        assertNotNull(first)
        assertNull(SingleInstanceLock.tryAcquire(dir))
        first.close()
        val again = SingleInstanceLock.tryAcquire(dir)
        assertNotNull(again)
        again.close()
    }

    @Test
    fun `another data directory is independent`() {
        val a = SingleInstanceLock.tryAcquire(temp.newFolder("a").toPath())
        val b = SingleInstanceLock.tryAcquire(temp.newFolder("b").toPath())
        assertNotNull(a)
        assertNotNull(b)
        a.close()
        b.close()
    }

    @Test
    fun `a directory that does not exist cannot be locked`() {
        assertNull(SingleInstanceLock.tryAcquire(temp.root.toPath().resolve("missing")))
    }
}

class LaunchFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `only existing files Encly can open are taken from the arguments`() {
        val backup = temp.newFile("vault.enclybak")
        val note = temp.newFile("a.md")
        val photo = temp.newFile("p.png")
        val args =
            arrayOf(backup.path, "--flag", photo.path, temp.root.resolve("missing.md").path, "bad\u0000path", note.path)
        assertEquals(listOf(backup.toPath(), note.toPath()), LaunchFiles.fromArguments(args))
        assertTrue(LaunchFiles.fromArguments(emptyArray()).isEmpty())
    }
}
