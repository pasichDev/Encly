package com.pasich.encly.desktop

import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.graphics.Color
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_now
import com.pasich.encly.desktop.platform.AwtLinkOpener
import com.pasich.encly.desktop.platform.DesktopAppInfo
import com.pasich.encly.desktop.platform.DesktopAppLocaleController
import com.pasich.encly.desktop.platform.DesktopAppWindow
import com.pasich.encly.desktop.platform.DesktopDeviceCapabilities
import com.pasich.encly.desktop.platform.DesktopDocumentPickers
import com.pasich.encly.desktop.platform.DesktopDocuments
import com.pasich.encly.desktop.platform.DesktopExternalLinks
import com.pasich.encly.desktop.platform.DesktopToaster
import com.pasich.encly.desktop.platform.DropRouter
import com.pasich.encly.desktop.platform.DroppedKind
import com.pasich.encly.desktop.platform.FileFilters
import com.pasich.encly.desktop.platform.GatedFileDialogs
import com.pasich.encly.desktop.platform.toDocumentRef
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.NavigationRequests
import com.pasich.encly.platform.ToastDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Frame
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalLinksTest {
    private val opener = RecordingLinks()
    private val links = DesktopExternalLinks(opener)

    @Test
    fun `web and mail addresses are opened`() {
        assertTrue(links.openUrl("https://example.org/privacy"))
        assertTrue(links.openUrl("http://example.org", chooser = true))
        assertTrue(links.writeMail("dev@example.org"))
        assertEquals(
            listOf("https://example.org/privacy", "http://example.org", "mailto:dev@example.org"),
            opener.opened.map(URI::toString),
        )
    }

    @Test
    fun `local files and unknown schemes never leave the app`() {
        assertFalse(links.openUrl("file:///etc/passwd"))
        assertFalse(links.openUrl("javascript:alert(1)"))
        assertFalse(links.openUrl("ms-settings:privacy"))
        assertFalse(links.openUrl("not a url at all"))
        assertFalse(links.openUrl("/just/a/path"))
        assertFalse(links.writeMail("bad address with spaces"))
        assertTrue(opener.opened.isEmpty())
    }

    @Test
    fun `a refused address is reported`() {
        val refusing = DesktopExternalLinks(RecordingLinks(accepts = false))
        assertFalse(refusing.openUrl("https://example.org"))
        assertFalse(refusing.writeMail("a@b.c"))
    }

    @Test
    fun `the AWT opener never throws on a headless machine`() {
        // Headless CI has no browser: the answer is false (or true on a desktop), never an exception.
        AwtLinkOpener.open(URI("https://example.org")).let { }
    }
}

class DocumentsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val documents = DesktopDocuments()

    @Test
    fun `a document is written, read, named and deleted`() {
        val ref = temp.root.toPath().resolve("note.enclybak").toDocumentRef()
        documents.openWrite(ref)!!.buffered().use { it.write(byteArrayOf(1, 2, 3)) }
        assertContentEquals(byteArrayOf(1, 2, 3), documents.openRead(ref)!!.buffered().use { it.readByteArray() })
        assertEquals("note.enclybak", documents.displayName(ref))
        documents.delete(ref)
        assertFalse(Files.exists(Path.of(ref.value)))
        documents.delete(ref)
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) = assertTrue(expected.contentEquals(actual))

    @Test
    fun `writing replaces the old content`() {
        val ref = temp.newFile("a.md").toPath().also { Files.writeString(it, "a long old content") }.toDocumentRef()
        documents.openWrite(ref)!!.buffered().use { it.write("new".encodeToByteArray()) }
        assertEquals("new", Files.readString(Path.of(ref.value)))
    }

    @Test
    fun `a missing file is an IO error and a bad path has no stream`() {
        val missing = temp.root.toPath().resolve("gone").toDocumentRef()
        kotlin.test.assertFailsWith<java.io.IOException> { documents.openRead(missing) }
        val bad = DocumentRef("bad\u0000path")
        assertNull(documents.openRead(bad))
        assertNull(documents.openWrite(bad))
        assertNull(documents.displayName(bad))
        documents.delete(bad)
        kotlin.test.assertFailsWith<java.io.IOException> { documents.openTree(bad, 10) }
    }

    @Test
    fun `a folder is walked without following symbolic links`() {
        val root = temp.newFolder("notes").toPath()
        Files.writeString(root.resolve("a.md"), "a")
        Files.createDirectory(root.resolve("sub"))
        Files.writeString(root.resolve("sub/b.md"), "b")
        val outside = temp.newFile("secret.txt").toPath().also { Files.writeString(it, "secret") }
        val linksSupported = runCatching { Files.createSymbolicLink(root.resolve("link.md"), outside) }.isSuccess

        val tree = documents.openTree(root.toDocumentRef(), maxEntries = 100)
        val top = tree.children(tree.rootId)
        assertEquals(setOf("a.md", "sub") + if (linksSupported) emptySet() else emptySet(), top.map { it.name }.toSet())
        val sub = top.single { it.name == "sub" }
        assertTrue(sub.isDirectory)
        assertEquals(listOf("b.md"), tree.children(sub.id).map { it.name })
        assertEquals(
            "a",
            tree.open(
                top.single {
                    it.name == "a.md"
                }.id,
            )!!.buffered().use { it.readByteArray().decodeToString() },
        )
    }

    @Test
    fun `the tree opens nothing outside its folder, no links and no directories`() {
        val root = temp.newFolder("notes").toPath()
        val inside = root.resolve("a.md").also { Files.writeString(it, "a") }
        val outside = temp.newFile("secret.txt").toPath()
        val tree = documents.openTree(root.toDocumentRef(), maxEntries = 100)
        assertNull(tree.open(outside.toString()))
        assertNull(tree.open(root.toString()))
        assertNull(tree.open(root.resolve("missing.md").toString()))
        assertNotNull(tree.open(inside.toString()))
        runCatching { Files.createSymbolicLink(root.resolve("link.md"), outside) }.onSuccess {
            assertNull(tree.open(root.resolve("link.md").toString()))
        }
        // Not a folder: no children.
        assertTrue(tree.children(inside.toString()).isEmpty())
    }

    @Test
    fun `listing stops at the entry limit`() {
        val root = temp.newFolder("many").toPath()
        repeat(10) { Files.writeString(root.resolve("$it.md"), "x") }
        val tree = documents.openTree(root.toDocumentRef(), maxEntries = 3)
        assertEquals(3, tree.children(tree.rootId).size)
    }
}

class FileFiltersAndKindsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `mime types become extensions, and anything generic means no filter`() {
        assertEquals(setOf("md", "markdown"), FileFilters.extensionsFor(arrayOf("text/markdown")))
        assertEquals(
            setOf("zip", "md", "markdown"),
            FileFilters.extensionsFor(arrayOf("application/zip", "text/markdown")),
        )
        assertEquals(emptySet(), FileFilters.extensionsFor(arrayOf("*/*")))
        assertEquals(emptySet(), FileFilters.extensionsFor(arrayOf(BackupFormat.MIME_TYPE)))
        assertEquals(emptySet(), FileFilters.extensionsFor(emptyArray()))
        assertEquals(emptySet(), FileFilters.extensionsFor(arrayOf("text/markdown", "application/x-unknown")))
    }

    @Test
    fun `dropped files are told apart by extension`() {
        fun kind(name: String) = DroppedKind.of(temp.root.toPath().resolve(name))
        assertEquals(DroppedKind.BACKUP, kind("vault.enclybak"))
        assertEquals(DroppedKind.BACKUP, kind("VAULT.ENCLYBAK"))
        assertEquals(DroppedKind.NOTES, kind("a.md"))
        assertEquals(DroppedKind.NOTES, kind("export.ZIP"))
        assertEquals(DroppedKind.NOTES, kind("keep.json"))
        assertNull(kind("photo.png"))
        assertNull(kind("noextension"))
        assertEquals(DroppedKind.FOLDER, DroppedKind.of(temp.newFolder("dir").toPath()))
    }
}

class DropRouterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val navigation = NavigationRequests()
    private var now = 0L
    private val router = DropRouter(navigation, post = { it() }, clock = { now })

    private fun file(name: String): Path = temp.newFile(name).toPath()

    private fun requested(): List<String> = drainRoutes(navigation)

    @Test
    fun `a backup answers the screen that asks for one file`() {
        var picked: DocumentRef? = null
        router.registerOneFile { picked = it }
        val backup = file("vault.enclybak")
        assertTrue(router.drop(listOf(backup)))
        assertEquals(backup.toDocumentRef(), picked)
        assertTrue(requested().isEmpty())
    }

    @Test
    fun `the newest screen gets the file and a closed screen none`() {
        val calls = mutableListOf<String>()
        router.registerOneFile { calls += "old" }
        val newest = router.registerOneFile { calls += "new" }
        router.drop(listOf(file("a.enclybak")))
        assertEquals(listOf("new"), calls)
        newest.close()
        router.drop(listOf(file("b.enclybak")))
        assertEquals(listOf("new", "old"), calls)
    }

    @Test
    fun `notes answer the import screen and a folder its folder picker`() {
        var many: List<DocumentRef> = emptyList()
        var folder: DocumentRef? = null
        router.registerManyFiles { many = it }
        router.registerFolder { folder = it }
        val a = file("a.md")
        val b = file("b.zip")
        router.drop(listOf(a, b, file("ignored.png")))
        assertEquals(listOf(a.toDocumentRef(), b.toDocumentRef()), many)
        val dir = temp.newFolder("dir").toPath()
        router.drop(listOf(dir))
        assertEquals(dir.toDocumentRef(), folder)
    }

    @Test
    fun `a backup wins over notes in one drop`() {
        var one: DocumentRef? = null
        var many: List<DocumentRef> = emptyList()
        router.registerOneFile { one = it }
        router.registerManyFiles { many = it }
        val backup = file("x.enclybak")
        router.drop(listOf(file("a.md"), backup))
        assertEquals(backup.toDocumentRef(), one)
        assertTrue(many.isEmpty())
    }

    @Test
    fun `files Encly cannot use are not routed`() {
        assertFalse(router.drop(listOf(file("photo.png"))))
        assertFalse(router.drop(emptyList()))
        assertTrue(requested().isEmpty())
    }

    @Test
    fun `with no screen waiting the matching screen is requested and gets the file when it appears`() {
        val backup = file("vault.enclybak")
        assertTrue(router.drop(listOf(backup)))
        assertEquals(listOf("BackupRoute"), requested())
        var picked: DocumentRef? = null
        router.registerOneFile { picked = it }
        assertEquals(backup.toDocumentRef(), picked)
    }

    @Test
    fun `notes with no import screen open ask for it`() {
        val note = file("a.md")
        router.drop(listOf(note))
        assertEquals(listOf("ImportNotesRoute"), requested())
        var many: List<DocumentRef> = emptyList()
        router.registerManyFiles { many = it }
        assertEquals(listOf(note.toDocumentRef()), many)
    }

    @Test
    fun `a folder with no screen open asks for the import screen`() {
        val dir = temp.newFolder("dir").toPath()
        router.drop(listOf(dir))
        assertEquals(listOf("ImportNotesRoute"), requested())
        var folder: DocumentRef? = null
        router.registerFolder { folder = it }
        assertEquals(dir.toDocumentRef(), folder)
    }

    @Test
    fun `a waiting file goes stale, and goes to the right kind of screen only`() {
        router.drop(listOf(file("a.enclybak")))
        now += DropRouter.PENDING_MILLIS + 1
        var picked: DocumentRef? = null
        router.registerOneFile { picked = it }
        assertNull(picked)

        router.drop(listOf(file("b.enclybak")))
        var many: List<DocumentRef>? = null
        router.registerManyFiles { many = it }
        assertNull(many)
        router.registerOneFile { picked = it }
        assertNotNull(picked)
    }
}

class PickersTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `gated dialogs mark the own dialog open while it is shown and closed after`() {
        val lifecycle = DesktopAppLifecycle()
        val inner = FakeFileDialogs()
        val gated = GatedFileDialogs(inner, lifecycle)
        val file = temp.newFile("a").toPath()
        inner.saveAnswer = file
        inner.openAnswer = listOf(file)
        inner.folderAnswer = file

        var saved: Path? = null
        var opened: List<Path> = emptyList()
        var folder: Path? = null
        gated.save("backup.enclybak") { saved = it }
        gated.open(multiple = true, extensions = setOf("md")) { opened = it }
        gated.folder { folder = it }
        assertEquals(file, saved)
        assertEquals(listOf(file), opened)
        assertEquals(file, folder)
        assertEquals(listOf("save:backup.enclybak", "open:true:md", "folder"), inner.calls)

        // While a dialog is open the window losing focus is not leaving the app.
        val slow = object : com.pasich.encly.desktop.platform.FileDialogs {
            override fun save(suggestedName: String, onResult: (Path?) -> Unit) {
                lifecycle.windowDeactivated()
                assertTrue(lifecycle.inForeground)
                onResult(null)
            }

            override fun open(multiple: Boolean, extensions: Set<String>, onResult: (List<Path>) -> Unit) = Unit

            override fun folder(onResult: (Path?) -> Unit) = Unit
        }
        GatedFileDialogs(slow, lifecycle).save("x") { }
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun `the AWT dialogs are built for each OS`() {
        for (os in com.pasich.encly.desktop.storage.DesktopOs.entries) {
            com.pasich.encly.desktop.platform.AwtFileDialogs(os) { null as Frame? }
        }
    }

    @Test
    fun `the frame the dialogs belong to is remembered`() {
        val parent = com.pasich.encly.desktop.platform.DialogParent()
        assertNull(parent.frame)
    }

    @Test
    fun `the pickers class is created with its collaborators`() {
        DesktopDocumentPickers(FakeFileDialogs(), DropRouter(NavigationRequests()))
    }
}

class HostServicesTest {
    @Test
    fun `the build version is read from the build resource`() {
        val info = DesktopAppInfo.fromBuild()
        assertEquals("Encly", info.appName)
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(info.versionName), info.versionName)
        assertTrue(info.donationsEnabled)
        assertFalse(info.storeRatingEnabled)
        assertTrue(info.storePageLinks.isEmpty())
        assertEquals("1.2.3", DesktopAppInfo("1.2.3").versionName)
    }

    @Test
    fun `a desktop has no dynamic colours`() {
        assertFalse(DesktopDeviceCapabilities.supportsDynamicColors())
    }

    @Test
    fun `the window excludes itself from capture and ignores the rest`() {
        val capture = RecordingCapture()
        var background: Color? = null
        val frame = object : java.awt.Window(null as Frame?) {}
        val window = DesktopAppWindow(capture, { frame }, { background = it })
        window.enforceSecure()
        assertEquals(listOf<java.awt.Window>(frame), capture.windows)
        window.setBackgroundColor(0xFF112233.toInt())
        assertEquals(Color(0xFF112233.toInt()), background)
        window.setLightStatusBar(true)
        window.hideKeyboard()
        window.showKeyboard()
        assertFalse(window.isChangingConfigurations)
        DesktopAppWindow(capture, { null }).enforceSecure()
        assertEquals(1, capture.windows.size)
    }

    @Test
    fun `the toaster shows a message as a snackbar in the in-app language resources`() {
        val toaster = DesktopToaster(Dispatchers.Unconfined)
        toaster.show(Res.string.lock_now, ToastDuration.LONG)
        val data = awaitSnackbar(toaster)
        assertEquals("Lock now", data.visuals.message)
        assertEquals(SnackbarDuration.Long, data.visuals.duration)
        data.dismiss()
        toaster.show(Res.string.lock_now, ToastDuration.SHORT)
        val next = awaitSnackbar(toaster)
        assertEquals(SnackbarDuration.Short, next.visuals.duration)
        next.dismiss()
        // The default dispatcher is the UI thread's.
        DesktopToaster()
    }

    private fun awaitSnackbar(toaster: DesktopToaster): androidx.compose.material3.SnackbarData {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            toaster.hostState.currentSnackbarData?.let { return it }
            Thread.sleep(10)
        }
        error("no snackbar was shown")
    }

    @Test
    fun `the language is stored, applied to the process and announced`() {
        val flags = MemoryKeyValueStore()
        val system = Locale.getDefault()
        val controller = DesktopAppLocaleController(flags, system)
        try {
            assertEquals(AppLanguage.SYSTEM, controller.current())
            controller.apply(AppLanguage.UKRAINIAN)
            assertEquals("uk", Locale.getDefault().language)
            assertEquals("uk", flags.getString("app_language", null))
            assertEquals(AppLanguage.UKRAINIAN, DesktopAppLocaleController(flags, system).current())
            DesktopAppLocaleController(flags, system).syncDefaultLocale()
            controller.apply(AppLanguage.SYSTEM)
            assertEquals(system, Locale.getDefault())
            assertFalse(flags.contains("app_language"))
            assertEquals(2, controller.revision.value)
        } finally {
            Locale.setDefault(system)
        }
    }

    @Test
    fun `a stored unknown language means the system language`() {
        val flags = MemoryKeyValueStore().also { it.values["app_language"] = "xx" }
        assertEquals(AppLanguage.SYSTEM, DesktopAppLocaleController(flags).current())
    }
}
