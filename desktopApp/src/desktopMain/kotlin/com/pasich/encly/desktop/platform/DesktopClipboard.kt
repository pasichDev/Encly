package com.pasich.encly.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.platform.SensitiveClipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.Clipboard as AwtClipboard

/**
 * Encly's clipboard policy on the desktop, the one Android applies: what the app copies out is cleared after
 * [CLEAR_AFTER_MS] unless something else was copied since. "Something else" is recognised by the clipboard's
 * text: if it still holds exactly what was armed, it is Encly's clip and it is emptied; if it holds anything
 * else, it is left alone. A clipboard that cannot be read (another program holds it) is emptied anyway, as on
 * Android: an Encly clip must not outlive its minute.
 *
 * Limit: AWT has no "sensitive" flag, so a clipboard history (Windows Win+V, a clipboard manager) may keep a
 * copy that this wipe cannot reach.
 */
class ClipboardWiper(
    private val clipboard: () -> AwtClipboard?,
    private val timer: MainThreadTimer,
    private val clearAfterMillis: Long = CLEAR_AFTER_MS,
) {
    private var armedText: String? = null
    private var cancel: (() -> Unit)? = null

    /** [text] was just put on the clipboard: empties it after the delay if it is still there. */
    fun arm(text: String) {
        cancel?.invoke()
        armedText = text
        cancel = timer.schedule(clearAfterMillis, ::wipeIfStillHeld)
    }

    /** Empties the clipboard now if it still holds what was armed (the app is closing). */
    fun wipeIfStillHeld() {
        val armed = armedText ?: return
        cancel?.invoke()
        cancel = null
        armedText = null
        val board = clipboard() ?: return
        val current = readText(board)
        if (current == null || current == armed) emptyClipboard(board)
    }

    /** Empties the clipboard now, whatever it holds. */
    fun clearNow() {
        cancel?.invoke()
        cancel = null
        armedText = null
        clipboard()?.let(::emptyClipboard)
    }

    private fun readText(board: AwtClipboard): String? = try {
        if (board.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            board.getData(DataFlavor.stringFlavor) as? String
        } else {
            // Something that is not text was copied since: not ours.
            EMPTY_OTHER
        }
    } catch (_: Exception) {
        null
    }

    private fun emptyClipboard(board: AwtClipboard) {
        try {
            board.setContents(StringSelection(""), null)
        } catch (_: IllegalStateException) {
            // The clipboard is held by another program; it stays as it is.
        }
    }

    companion object {
        const val CLEAR_AFTER_MS = 60_000L

        // A value no armed text can equal: the clipboard holds something that is not text.
        private const val EMPTY_OTHER = "\u0000not-text\u0000"
    }
}

/** [SensitiveClipboard] over the system clipboard of AWT. */
class DesktopSensitiveClipboard(private val clipboard: () -> AwtClipboard?, private val wiper: ClipboardWiper) :
    SensitiveClipboard {
    // The desktop shows nothing when something is copied: the app says so itself.
    override val systemConfirmsCopy: Boolean = false

    override fun copy(label: String, text: String): Boolean {
        val board = clipboard() ?: return false
        return try {
            board.setContents(StringSelection(text), null)
            wiper.arm(text)
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    override fun clear() = wiper.clearNow()
}

/**
 * A [Clipboard] that arms [ClipboardWiper] for everything the Compose text fields copy (Ctrl/Cmd+C and the
 * cut and copy of the context menu), so the policy covers the editor as well as the explicit copy buttons.
 */
private class SensitiveComposeClipboard(private val delegate: Clipboard, private val wiper: ClipboardWiper) :
    Clipboard {
    override val nativeClipboard: Any get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        delegate.setClipEntry(clipEntry)
        val text = clipEntry?.let(::textOf)
        if (clipEntry == null) {
            wiper.clearNow()
        } else if (text != null) {
            wiper.arm(text)
        }
    }

    @Suppress("ReturnCount") // Guards before the read.
    private fun textOf(entry: ClipEntry): String? {
        val transferable = entry.nativeClipEntry as? Transferable ?: return null
        if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
        return try {
            transferable.getTransferData(DataFlavor.stringFlavor) as? String
        } catch (_: Exception) {
            null
        }
    }
}

/** Routes every Compose copy under [content] through [wiper]. */
@Composable
fun SensitiveClipboardBoundary(wiper: ClipboardWiper, content: @Composable () -> Unit) {
    val platform = LocalClipboard.current
    val clipboard = remember(platform, wiper) { SensitiveComposeClipboard(platform, wiper) }
    CompositionLocalProvider(LocalClipboard provides clipboard, content = content)
}
