package com.pasich.encly.desktop

import com.pasich.encly.desktop.storage.AppDirectories
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.storage.FileKeyValueStore
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDirectoriesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun `windows uses APPDATA and falls back to the roaming profile`() {
        assertEquals(
            Paths.get("C:\\Users\\a\\AppData\\Roaming", "Encly"),
            AppDirectories.dataDirectory(
                DesktopOs.WINDOWS,
                env("APPDATA" to "C:\\Users\\a\\AppData\\Roaming"),
                "C:\\Users\\a",
            ),
        )
        assertEquals(
            Paths.get("home", "AppData", "Roaming", "Encly"),
            AppDirectories.dataDirectory(DesktopOs.WINDOWS, env("APPDATA" to " "), "home"),
        )
    }

    @Test
    fun `macos uses Application Support`() {
        assertEquals(
            Paths.get("/Users/a", "Library", "Application Support", "Encly"),
            AppDirectories.dataDirectory(DesktopOs.MACOS, env(), "/Users/a"),
        )
    }

    @Test
    fun `linux uses XDG_DATA_HOME only when it is absolute`() {
        assertEquals(
            Paths.get("/data/xdg", "encly"),
            AppDirectories.dataDirectory(DesktopOs.LINUX, env("XDG_DATA_HOME" to "/data/xdg"), "/home/a"),
        )
        val fallback = Paths.get("/home/a", ".local", "share", "encly")
        assertEquals(fallback, AppDirectories.dataDirectory(DesktopOs.LINUX, env(), "/home/a"))
        assertEquals(fallback, AppDirectories.dataDirectory(DesktopOs.LINUX, env("XDG_DATA_HOME" to ""), "/home/a"))
        assertEquals(fallback, AppDirectories.dataDirectory(DesktopOs.LINUX, env("XDG_DATA_HOME" to "rel"), "/home/a"))
    }

    @Test
    fun `os names map to the layouts`() {
        assertEquals(DesktopOs.WINDOWS, DesktopOs.fromName("Windows 11"))
        assertEquals(DesktopOs.MACOS, DesktopOs.fromName("Mac OS X"))
        assertEquals(DesktopOs.MACOS, DesktopOs.fromName("Darwin"))
        assertEquals(DesktopOs.LINUX, DesktopOs.fromName("Linux"))
        assertEquals(DesktopOs.LINUX, DesktopOs.fromName("FreeBSD"))
        DesktopOs.current()
    }

    @Test
    fun `the system directory is an absolute path of this user`() {
        assertTrue(AppDirectories.systemDataDirectory().isAbsolute)
    }

    @Test
    fun `a private directory is owner only where POSIX permissions exist`() {
        assumeTrue(Files.getFileStore(temp.root.toPath()).supportsFileAttributeView("posix"))
        val dir = temp.root.toPath().resolve("a/b/Encly")
        AppDirectories.createPrivate(dir)
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
    }

    @Test
    fun `an existing loose directory is tightened`() {
        assumeTrue(Files.getFileStore(temp.root.toPath()).supportsFileAttributeView("posix"))
        val dir = temp.newFolder("loose").toPath()
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"))
        AppDirectories.createPrivate(dir)
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
    }

    @Test
    fun `a directory is created without POSIX permissions too`() {
        // Whatever the file store supports, creating works and is idempotent.
        val dir = temp.root.toPath().resolve("plain")
        assertEquals(dir, AppDirectories.createPrivate(dir))
        assertEquals(dir, AppDirectories.createPrivate(dir))
        assertTrue(Files.isDirectory(dir))
    }
}

class FileKeyValueStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val now = Executor { it.run() }

    private fun file(): Path = temp.root.toPath().resolve("flags.properties")

    @Test
    fun `values are typed and survive a restart`() {
        val store = FileKeyValueStore(file(), now)
        assertTrue(
            store.commit {
                putString("s", "héllo = wörld\nline")
                putBoolean("b", true)
                putLong("l", 1L shl 40)
            },
        )
        val again = FileKeyValueStore(file(), now)
        assertEquals("héllo = wörld\nline", again.getString("s", null))
        assertTrue(again.getBoolean("b", false))
        assertEquals(1L shl 40, again.getLong("l", 0L))
        assertTrue(again.contains("s"))
    }

    @Test
    fun `defaults come back for a missing or mistyped key`() {
        val store = FileKeyValueStore(file(), now)
        store.edit { putString("text", "not a number") }
        assertEquals("d", store.getString("none", "d"))
        assertEquals(7L, store.getLong("none", 7L))
        assertEquals(9L, store.getLong("text", 9L))
        assertTrue(store.getBoolean("text", true))
        assertFalse(store.contains("none"))
    }

    @Test
    fun `remove and clear are persisted`() {
        val store = FileKeyValueStore(file(), now)
        store.commit {
            putString("a", "1")
            putString("b", "2")
        }
        store.commit { remove("a") }
        assertNull(FileKeyValueStore(file(), now).getString("a", null))
        store.commit { clear() }
        assertFalse(FileKeyValueStore(file(), now).contains("b"))
    }

    @Test
    fun `edit returns at once and writes later on the writer`() {
        val queued = mutableListOf<Runnable>()
        val store = FileKeyValueStore(file()) { queued += it }
        store.edit { putString("k", "v") }
        // In memory at once, on disk only after the writer ran.
        assertEquals("v", store.getString("k", null))
        assertFalse(Files.exists(file()))
        queued.forEach(Runnable::run)
        assertEquals("v", FileKeyValueStore(file(), now).getString("k", null))
    }

    @Test
    fun `a write leaves no temp file and replaces the old content whole`() {
        val store = FileKeyValueStore(file(), now)
        repeat(5) { i -> store.commit { putLong("n", i.toLong()) } }
        val names = Files.list(temp.root.toPath()).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(listOf("flags.properties"), names)
        assertEquals(4L, FileKeyValueStore(file(), now).getLong("n", -1))
    }

    @Test
    fun `a failed write keeps the old file intact and reports false`() {
        val store = FileKeyValueStore(file(), now)
        assertTrue(store.commit { putString("k", "old") })
        val before = Files.readAllBytes(file())
        // Make the target a directory: the rename over it fails, as a full disk or a lock would.
        Files.delete(file())
        Files.createDirectory(file())
        Files.createFile(file().resolve("keep"))
        assertFalse(store.commit { putString("k", "new") })
        assertTrue(Files.exists(file().resolve("keep")))
        val names = Files.list(temp.root.toPath()).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(listOf("flags.properties"), names, "no temp file is left behind")
        assertTrue(before.isNotEmpty())
        // The value is still held in memory.
        assertEquals("new", store.getString("k", null))
    }

    @Test
    fun `an unreadable file starts empty`() {
        Files.write(file(), byteArrayOf(0x5c, 0x75, 0x7a))
        val store = FileKeyValueStore(file(), now)
        assertFalse(store.contains("anything"))
        assertTrue(store.commit { putString("k", "v") })
        assertEquals("v", FileKeyValueStore(file(), now).getString("k", null))
    }

    @Test
    fun `a file in a directory that does not exist yet is created`() {
        val nested = temp.root.toPath().resolve("a/b/flags.properties")
        assertTrue(FileKeyValueStore(nested, now).commit { putBoolean("x", true) })
        assertTrue(Files.isRegularFile(nested))
    }

    @Test
    fun `the default writer persists in the background`() {
        val store = FileKeyValueStore(file())
        store.edit { putString("async", "2") }
        // commit writes the whole current state, the asynchronous edit before it included.
        assertTrue(store.commit { putString("sync", "1") })
        assertEquals("2", FileKeyValueStore(file()).getString("async", null))
    }
}
