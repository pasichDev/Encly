package com.pasich.encly.ios.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import com.pasich.encly.core.platform.MainThreadTimer
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIPasteboard
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a Compose text field's copy does to the pasteboard. */
@OptIn(ExperimentalComposeUiApi::class)
class SensitiveComposeClipboardTest {
    /** The platform clipboard of Compose; any write to it is a failure of the policy. */
    private class ForbiddenDelegate : Clipboard {
        var writes = 0
        override val nativeClipboard: NativeClipboard get() = UIPasteboard.generalPasteboard

        override suspend fun getClipEntry(): ClipEntry? = null

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            writes++
        }
    }

    private val delegate = ForbiddenDelegate()
    private val policy = IosClipboardPolicy(MainThreadTimer.None)
    private val clipboard = SensitiveComposeClipboard(delegate, policy)

    @AfterTest
    fun cleanUp() = policy.clearNow()

    @Test
    fun aCopiedTextReachesThePasteboardOnlyThroughThePolicy() {
        runBlocking { clipboard.setClipEntry(ClipEntry.withPlainText("note line")) }

        assertEquals(0, delegate.writes)
        // A test executable may have no pasteboard server ("Cannot connect to pasteboard server"): then there is
        // nothing to read back, and the check above is the one that matters.
        UIPasteboard.generalPasteboard.string?.let { assertEquals("note line", it) }
    }

    @Test
    fun aNullEntryEmptiesThePasteboardAndStillNeverTouchesTheDelegate() {
        policy.copy("kept")

        runBlocking { clipboard.setClipEntry(null) }

        assertEquals(0, delegate.writes)
        assertNull(UIPasteboard.generalPasteboard.string)
        assertTrue(UIPasteboard.generalPasteboard.items.isEmpty())
    }
}
