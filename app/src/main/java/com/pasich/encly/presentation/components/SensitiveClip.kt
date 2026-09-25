package com.pasich.encly.presentation.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard

/**
 * Encly's clipboard policy, for everything copied out of the app:
 *
 * - every clip is marked sensitive ([ClipDescription.EXTRA_IS_SENSITIVE]), so Android 13+
 *   does not preview its content in the copy confirmation, and keyboards that honour the flag
 *   do not suggest it;
 * - it is cleared after [CLEAR_AFTER_MS], unless something else was copied since.
 *
 * Compose text fields copy through [LocalClipboard], which [SensitiveClipboardBoundary]
 * replaces at the app root; other code copies with [SensitiveClip.copy].
 */
object SensitiveClip {
    const val CLEAR_AFTER_MS = 60_000L

    /** The flag's value on API 33+; the same key is harmless on older versions. */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** Copies [text] under [label] as a sensitive clip that clears itself. */
    fun copy(clipboard: ClipboardManager, label: CharSequence, text: CharSequence) {
        val clip = ClipData.newPlainText(label, text)
        markSensitive(clip)
        clipboard.setPrimaryClip(clip)
        scheduleClear(clipboard)
    }

    fun markSensitive(clip: ClipData) {
        val extras = clip.description.extras ?: PersistableBundle()
        extras.putBoolean(
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                EXTRA_IS_SENSITIVE
            },
            true,
        )
        clip.description.extras = extras
    }

    /**
     * Clears the clipboard after [CLEAR_AFTER_MS] if it still holds the clip just set. The clip
     * is recognised by its timestamp; when the system hides the description (the app is in the
     * background, Android 10+), it is cleared anyway: an Encly clip must not outlive its minute.
     */
    fun scheduleClear(clipboard: ClipboardManager) {
        val stamp = timestampOf(clipboard)
        handler.postDelayed(
            {
                val current = timestampOf(clipboard)
                if (current == null || current == stamp) clear(clipboard)
            },
            CLEAR_AFTER_MS,
        )
    }

    /** Empties the clipboard now (e.g. after the recovery phrase was pasted). */
    fun clear(clipboard: ClipboardManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: RuntimeException) {
            // A clipboard the system refuses to touch stays as it is.
        }
    }

    private fun timestampOf(clipboard: ClipboardManager): Long? = try {
        clipboard.primaryClipDescription?.timestamp
    } catch (_: RuntimeException) {
        null
    }
}

/** A [Clipboard] that applies [SensitiveClip] to everything set through it. */
private class SensitiveClipboard(private val delegate: Clipboard) : Clipboard {
    override val nativeClipboard: ClipboardManager get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry?.clipData?.let(SensitiveClip::markSensitive)
        delegate.setClipEntry(clipEntry)
        if (clipEntry != null) SensitiveClip.scheduleClear(nativeClipboard)
    }
}

/** Routes every Compose copy under [content] through [SensitiveClip]. */
@Composable
fun SensitiveClipboardBoundary(content: @Composable () -> Unit) {
    val platform = LocalClipboard.current
    val clipboard = remember(platform) { SensitiveClipboard(platform) }
    CompositionLocalProvider(LocalClipboard provides clipboard, content = content)
}
