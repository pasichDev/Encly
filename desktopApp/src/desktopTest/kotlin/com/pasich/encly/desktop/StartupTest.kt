package com.pasich.encly.desktop

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `a directory that does not exist cannot be locked and is no second start`() {
        assertFailsWith<IOException> { SingleInstanceLock.tryAcquire(temp.root.toPath().resolve("missing")) }
    }
}

class InstanceChannelTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a second start hands its files to the first and is answered`() {
        val dir = temp.root.toPath()
        val backup = temp.newFile("vault.enclybak").toPath()
        val note = temp.newFile("a.md").toPath()
        val received = LinkedBlockingQueue<List<Path>>()
        val channel = InstanceChannel.listen(dir) { received.put(it) }
        assertNotNull(channel, "a socket can be made in the test directory")
        try {
            assertTrue(InstanceChannel.send(dir, listOf(backup, note)))
            assertEquals(listOf(backup, note), received.poll(5, TimeUnit.SECONDS))
            // No files: the first only comes to the front.
            assertTrue(InstanceChannel.send(dir, emptyList()))
            assertEquals(emptyList(), received.poll(5, TimeUnit.SECONDS))
        } finally {
            channel.close()
        }
    }

    @Test
    fun `the first takes only files it could use itself`() {
        val dir = temp.root.toPath()
        val photo = temp.newFile("p.png").toPath()
        val missing = temp.root.toPath().resolve("missing.md")
        val note = temp.newFile("a.md").toPath()
        val received = LinkedBlockingQueue<List<Path>>()
        val channel = InstanceChannel.listen(dir) { received.put(it) }
        assertNotNull(channel)
        try {
            assertTrue(InstanceChannel.send(dir, listOf(photo, missing, note)))
            assertEquals(listOf(note), received.poll(5, TimeUnit.SECONDS))
        } finally {
            channel.close()
        }
    }

    @Test
    fun `nothing is sent when no first start listens, and a closed channel is gone`() {
        val dir = temp.root.toPath()
        assertFalse(InstanceChannel.send(dir, emptyList()))
        InstanceChannel.listen(dir) { }?.close()
        assertFalse(InstanceChannel.send(dir, emptyList()))
    }

    @Test
    fun `a leftover socket file of a crashed start does not stop the next one listening`() {
        val dir = temp.root.toPath()
        Files.writeString(dir.resolve(".instance.sock"), "stale")
        val channel = InstanceChannel.listen(dir) { }
        assertNotNull(channel)
        channel.close()
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
