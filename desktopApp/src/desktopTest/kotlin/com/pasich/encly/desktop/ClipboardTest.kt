package com.pasich.encly.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.pasich.encly.desktop.platform.ClipboardWiper
import com.pasich.encly.desktop.platform.DesktopSensitiveClipboard
import com.pasich.encly.desktop.platform.SensitiveClipboardBoundary
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.platform.Clipboard as ComposeClipboard

class ClipboardWiperTest {
    private val board = Clipboard("test")
    private val timer = ManualTimer()
    private val wiper = ClipboardWiper({ board }, timer)
    private val clipboard = DesktopSensitiveClipboard({ board }, wiper)

    private fun text(): String? = if (board.isDataFlavorAvailable(
            DataFlavor.stringFlavor,
        )
    ) {
        board.getData(DataFlavor.stringFlavor) as String
    } else {
        null
    }

    @Test
    fun `a copy puts the text on the clipboard and arms the wipe for a minute`() {
        assertTrue(clipboard.copy("label", "secret"))
        assertEquals("secret", text())
        assertEquals(ClipboardWiper.CLEAR_AFTER_MS, timer.pending.single().delayMs)
        assertEquals(60_000L, ClipboardWiper.CLEAR_AFTER_MS)
    }

    @Test
    fun `after the minute the clipboard is emptied if it still holds the copy`() {
        clipboard.copy("label", "secret")
        timer.fireAll()
        assertEquals("", text())
    }

    @Test
    fun `something copied since is left alone`() {
        clipboard.copy("label", "secret")
        board.setContents(StringSelection("something else"), null)
        timer.fireAll()
        assertEquals("something else", text())
    }

    @Test
    fun `a non text clip copied since is left alone`() {
        clipboard.copy("label", "secret")
        board.setContents(
            object : Transferable {
                private val flavor = DataFlavor("application/x-test;class=java.lang.Object", "test")

                override fun getTransferDataFlavors() = arrayOf(flavor)

                override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == this.flavor

                override fun getTransferData(flavor: DataFlavor): Any = Any()
            },
            null,
        )
        timer.fireAll()
        assertFalse(board.isDataFlavorAvailable(DataFlavor.stringFlavor))
    }

    @Test
    fun `a second copy replaces the first wipe`() {
        clipboard.copy("label", "one")
        clipboard.copy("label", "two")
        assertEquals(1, timer.pending.size)
        timer.fireAll()
        assertEquals("", text())
    }

    @Test
    fun `a clipboard that cannot be read is emptied anyway`() {
        val unreadable = object : Clipboard("unreadable") {
            var emptied = false

            override fun isDataFlavorAvailable(flavor: DataFlavor): Boolean = throw IllegalStateException("busy")

            override fun setContents(contents: Transferable, owner: ClipboardOwner?) {
                if (contents.getTransferData(DataFlavor.stringFlavor) == "") emptied = true
                super.setContents(contents, owner)
            }
        }
        val local = ClipboardWiper({ unreadable }, timer)
        local.arm("secret")
        timer.fireAll()
        assertTrue(unreadable.emptied)
    }

    @Test
    fun `clear empties it now and cancels the wipe`() {
        clipboard.copy("label", "secret")
        clipboard.clear()
        assertEquals("", text())
        assertTrue(timer.pending.isEmpty())
    }

    @Test
    fun `quitting wipes what is still there, once`() {
        clipboard.copy("label", "secret")
        wiper.wipeIfStillHeld()
        assertEquals("", text())
        board.setContents(StringSelection("later"), null)
        wiper.wipeIfStillHeld()
        assertEquals("later", text())
    }

    @Test
    fun `a missing clipboard copies nothing and wipes nothing`() {
        val none = DesktopSensitiveClipboard({ null }, ClipboardWiper({ null }, timer))
        assertFalse(none.copy("label", "secret"))
        none.clear()
        ClipboardWiper({ null }, timer).apply {
            arm("x")
            wipeIfStillHeld()
        }
    }

    @Test
    fun `a clipboard held by another program refuses the copy`() {
        val busy = object : Clipboard("busy") {
            override fun setContents(contents: Transferable?, owner: ClipboardOwner?) =
                throw IllegalStateException("busy")
        }
        assertFalse(DesktopSensitiveClipboard({ busy }, ClipboardWiper({ busy }, timer)).copy("l", "t"))
        // Wiping a busy clipboard leaves it as it is.
        ClipboardWiper({ busy }, timer).apply {
            arm("x")
            wipeIfStillHeld()
        }
        assertFalse(clipboard.systemConfirmsCopy)
    }
}

class SensitiveClipWriterTest {
    private val board = Clipboard("test")
    private val timer = ManualTimer()
    private val written = mutableListOf<String>()
    private var accepts = true
    private val wiper = ClipboardWiper({ board }, timer, writer = { text ->
        written += text
        accepts
    })
    private val clipboard = DesktopSensitiveClipboard({ board }, wiper)

    @Test
    fun `a copy goes out through the marked writer and never through AWT`() {
        assertTrue(clipboard.copy("label", "secret"))

        assertEquals(listOf("secret"), written)
        // AWT was not touched: its clipboard has nothing (the writer would have put the text on the real one).
        assertFalse(board.isDataFlavorAvailable(DataFlavor.stringFlavor))
        // The wipe is armed all the same.
        assertEquals(ClipboardWiper.CLEAR_AFTER_MS, timer.pending.single().delayMs)
    }

    @Test
    fun `a writer that cannot write falls back to AWT`() {
        accepts = false

        assertTrue(clipboard.copy("label", "secret"))

        assertEquals("secret", board.getData(DataFlavor.stringFlavor))
        assertEquals(1, timer.pending.size)
    }

    @Test
    fun `writing sensitive text is refused without a writer`() {
        assertFalse(ClipboardWiper({ board }, timer).writeSensitive("secret"))
        assertTrue(timer.pending.isEmpty())
    }
}

/**
 * The marked clipboard against the real OS clipboard of the machine. It overwrites the user's clipboard, so it runs
 * only on its own OS and only when `ENCLY_NATIVE_IT=1` is set.
 */
class NativeClipboardTest {
    private fun assumeNative(osPrefix: String) {
        org.junit.Assume.assumeTrue("ENCLY_NATIVE_IT is not set", System.getenv("ENCLY_NATIVE_IT") == "1")
        org.junit.Assume.assumeTrue(System.getProperty("os.name").lowercase().startsWith(osPrefix))
    }

    @Test
    fun `macos marks the text as concealed and transient`() {
        assumeNative("mac")
        val pasteboard = com.pasich.encly.desktop.nativeos.NativeClipboard.forOs(
            com.pasich.encly.desktop.storage.DesktopOs.MACOS,
        ) as com.pasich.encly.desktop.nativeos.MacPasteboard

        assertTrue(pasteboard.write("encly-clip-test"))

        val types = pasteboard.types()
        assertTrue("public.utf8-plain-text" in types, "types: $types")
        assertTrue("org.nspasteboard.ConcealedType" in types, "types: $types")
        assertTrue("org.nspasteboard.TransientType" in types, "types: $types")
        val text = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor)
        assertEquals("encly-clip-test", text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(""), null)
    }

    @Test
    fun `windows marks the text out of history and cloud sync`() {
        assumeNative("windows")
        val clipboard = com.pasich.encly.desktop.nativeos.NativeClipboard.forOs(
            com.pasich.encly.desktop.storage.DesktopOs.WINDOWS,
        ) as com.pasich.encly.desktop.nativeos.WindowsClipboard

        assertTrue(clipboard.write("encly-clip-test"))

        for (name in listOf(
            com.pasich.encly.desktop.nativeos.WindowsClipboard.EXCLUDE_FROM_MONITORS,
            com.pasich.encly.desktop.nativeos.WindowsClipboard.CAN_INCLUDE_IN_HISTORY,
            com.pasich.encly.desktop.nativeos.WindowsClipboard.CAN_UPLOAD_TO_CLOUD,
        )) {
            assertTrue(clipboard.hasMarker(name), name)
        }
        val text = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor)
        assertEquals("encly-clip-test", text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(""), null)
    }
}

@OptIn(ExperimentalTestApi::class)
class ComposeClipboardBoundaryTest {
    private class FakeComposeClipboard : ComposeClipboard {
        var entry: ClipEntry? = null
        override val nativeClipboard: Any = "native"

        override suspend fun getClipEntry(): ClipEntry? = entry

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }
    }

    @Composable
    private fun Capture(platform: ComposeClipboard, wiper: ClipboardWiper, out: (ComposeClipboard) -> Unit) {
        CompositionLocalProvider(LocalClipboard provides platform) {
            SensitiveClipboardBoundary(wiper) { out(LocalClipboard.current) }
        }
    }

    @Test
    fun `text copied in the editor is wiped like an explicit copy`() = runComposeUiTest {
        val board = Clipboard("compose")
        val timer = ManualTimer()
        val platform = FakeComposeClipboard()
        var wrapped: ComposeClipboard? = null
        setContent { Capture(platform, ClipboardWiper({ board }, timer)) { wrapped = it } }
        val clipboard = wrapped!!
        assertEquals("native", clipboard.nativeClipboard)

        board.setContents(StringSelection("from the editor"), null)
        runBlocking { clipboard.setClipEntry(ClipEntry(StringSelection("from the editor"))) }
        assertEquals(1, timer.pending.size)
        assertEquals(
            "from the editor",
            runBlocking {
                (clipboard.getClipEntry()!!.nativeClipEntry as Transferable).getTransferData(DataFlavor.stringFlavor)
            },
        )
        timer.fireAll()
        assertEquals("", board.getData(DataFlavor.stringFlavor))
    }

    @Test
    fun `text copied in the editor goes out marked and is not put there plain first`() = runComposeUiTest {
        val board = Clipboard("compose")
        val timer = ManualTimer()
        val platform = FakeComposeClipboard()
        val written = mutableListOf<String>()
        var wrapped: ComposeClipboard? = null
        setContent {
            Capture(
                platform,
                ClipboardWiper({ board }, timer, writer = {
                    written += it
                    true
                }),
            ) { wrapped = it }
        }

        runBlocking { wrapped!!.setClipEntry(ClipEntry(StringSelection("from the editor"))) }

        assertEquals(listOf("from the editor"), written)
        assertNull(platform.entry, "the delegate would have written the text unmarked")
        assertEquals(1, timer.pending.size)
    }

    @Test
    fun `non text entries are passed on and arm nothing, clearing clears`() = runComposeUiTest {
        val board = Clipboard("compose")
        val timer = ManualTimer()
        var wrapped: ComposeClipboard? = null
        setContent { Capture(FakeComposeClipboard(), ClipboardWiper({ board }, timer)) { wrapped = it } }
        val clipboard = wrapped!!
        runBlocking { clipboard.setClipEntry(ClipEntry("not a transferable")) }
        assertTrue(timer.pending.isEmpty())
        runBlocking {
            clipboard.setClipEntry(
                ClipEntry(
                    object : Transferable {
                        override fun getTransferDataFlavors() = emptyArray<DataFlavor>()

                        override fun isDataFlavorSupported(flavor: DataFlavor) = false

                        override fun getTransferData(flavor: DataFlavor): Any = throw UnsupportedOperationException()
                    },
                ),
            )
        }
        assertTrue(timer.pending.isEmpty())
        board.setContents(StringSelection("x"), null)
        runBlocking { clipboard.setClipEntry(null) }
        assertEquals("", board.getData(DataFlavor.stringFlavor))
        assertNull(runBlocking { FakeComposeClipboard().getClipEntry() })
    }
}
