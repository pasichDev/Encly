@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.platform.SensitiveClipboard
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly

/**
 * Encly's clipboard policy on iOS, the one Android applies: what the app copies out is marked sensitive, and is
 * cleared after [CLEAR_AFTER_SECONDS] unless something else was copied since.
 * - "Sensitive": the item is `localOnly`, so it does not travel to the user's other devices through Universal
 *   Clipboard, and has an `expirationDate`, so the system removes it itself when the time is up, even if the app
 *   no longer runs. (iOS shows no copy preview and has no suggestion flag to set; the paste prompt of iOS 16+
 *   only asks other apps to be allowed to read it.)
 * - "Unless something else was copied since": expiry only affects the items it was set on; and a timer in the app
 *   also empties the pasteboard if its change count is still the one Encly's copy made.
 *
 * Limit: a clipboard manager or a keyboard the user installed that reads the pasteboard while the clip is there
 * may keep a copy, as with the Android and desktop clipboards.
 */
internal class IosClipboardPolicy(private val timer: MainThreadTimer) {
    private var armedChange: Long = NONE
    private var cancel: (() -> Unit)? = null

    /** Puts [text] on the pasteboard as Encly's sensitive clip. */
    fun copy(text: String) {
        val board = UIPasteboard.generalPasteboard
        board.setItems(
            listOf(mapOf<Any?, Any?>(PLAIN_TEXT to text)),
            options = mapOf<Any?, Any?>(
                UIPasteboardOptionLocalOnly to true,
                UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(CLEAR_AFTER_SECONDS),
            ),
        )
        arm(board)
    }

    /**
     * Something copied by the system's own text editing (a field's copy or cut) is on the pasteboard now: it gets
     * the same policy. Items that are not text are left to the system.
     */
    fun adoptCurrent() {
        val text = UIPasteboard.generalPasteboard.string ?: return
        copy(text)
    }

    /** Empties the pasteboard now, whatever it holds. */
    fun clearNow() {
        cancel?.invoke()
        cancel = null
        armedChange = NONE
        UIPasteboard.generalPasteboard.setItems(emptyList<Any>(), options = emptyMap<Any?, Any?>())
    }

    private fun arm(board: UIPasteboard) {
        cancel?.invoke()
        armedChange = board.changeCount
        cancel = timer.schedule(CLEAR_AFTER_SECONDS.toLong() * MILLIS, ::wipeIfStillHeld)
    }

    /** Empties the pasteboard if it is still the clip Encly made. */
    fun wipeIfStillHeld() {
        val armed = armedChange
        cancel = null
        armedChange = NONE
        if (armed == NONE) return
        val board = UIPasteboard.generalPasteboard
        if (board.changeCount == armed) board.setItems(emptyList<Any>(), options = emptyMap<Any?, Any?>())
    }

    companion object {
        const val CLEAR_AFTER_SECONDS = 60.0
        private const val MILLIS = 1000L
        private const val NONE = -1L
        private const val PLAIN_TEXT = "public.utf8-plain-text"
    }
}

/** [SensitiveClipboard] over the pasteboard, by [IosClipboardPolicy]. */
internal class IosSensitiveClipboard(private val policy: IosClipboardPolicy) : SensitiveClipboard {
    // iOS shows nothing when something is copied: the app says so itself.
    override val systemConfirmsCopy: Boolean = false

    override fun copy(label: String, text: String): Boolean {
        policy.copy(text)
        return true
    }

    override fun clear() = policy.clearNow()
}

/**
 * A [Clipboard] that applies [IosClipboardPolicy] to everything the Compose text fields copy (the copy and cut of
 * the edit menu), so the policy covers the editor as well as the explicit copy buttons.
 */
private class SensitiveComposeClipboard(private val delegate: Clipboard, private val policy: IosClipboardPolicy) :
    Clipboard {
    override val nativeClipboard: NativeClipboard get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        delegate.setClipEntry(clipEntry)
        if (clipEntry == null) policy.clearNow() else policy.adoptCurrent()
    }
}

/** Routes every Compose copy under [content] through [policy]. */
@Composable
internal fun SensitiveClipboardBoundary(policy: IosClipboardPolicy, content: @Composable () -> Unit) {
    val platform = LocalClipboard.current
    val clipboard = remember(platform, policy) { SensitiveComposeClipboard(platform, policy) }
    CompositionLocalProvider(LocalClipboard provides clipboard, content = content)
}
