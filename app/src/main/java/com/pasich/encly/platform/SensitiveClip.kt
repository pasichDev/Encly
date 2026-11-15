package com.pasich.encly.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Encly's clipboard policy, for everything copied out of the app:
 *
 * - every clip is marked sensitive ([ClipDescription.EXTRA_IS_SENSITIVE]), so Android 13+
 *   does not preview its content in the copy confirmation, and keyboards that honour the flag
 *   do not suggest it;
 * - it is cleared after [CLEAR_AFTER_MS], unless something else was copied since;
 * - it is cleared when the vault locks, and after the process was killed.
 *
 * Only Encly's own clip is ever cleared. It is recognised by the timestamp of its description. From Android 10 the
 * system hides the description from an app that is not in front, so a background read says nothing: the clip is then
 * not cleared blindly (that could wipe something the user copied elsewhere); the stamp stays pending and the next
 * foreground (process ON_START) checks it again and clears the clip if it still carries it. The pending stamp, when
 * it was copied and whether a lock made it due are kept in a small preferences file ([install]), so a process
 * killed before the minute ran out clears at the next start too. The file holds a timestamp only, never content, and
 * the backup rules exclude all app preferences.
 *
 * Compose text fields copy through [LocalClipboard], which [SensitiveClipboardBoundary]
 * replaces at the app root; other code copies with [SensitiveClip.copy].
 */
@Suppress("TooManyFunctions") // One clipboard policy: copy, mark, schedule, resume, lock, clear.
object SensitiveClip {
    const val CLEAR_AFTER_MS = 60_000L

    /** The flag's value on API 33+; the same key is harmless on older versions. */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private const val PREFS_NAME = "encly_clip_pending"
    private const val KEY_STAMP = "stamp"
    private const val KEY_COPIED_AT = "copied_at"
    private const val KEY_DUE = "due"

    /** Reads of an unreadable clipboard right after a foreground (the window may not hold focus yet). */
    private const val FOREGROUND_RETRIES = 4
    private const val FOREGROUND_RETRY_MS = 250L

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** The clip Encly put on the clipboard last and has not cleared: [stamp], when ([copiedAt], wall clock), [due]. */
    private class Pending(val stamp: Long, val copiedAt: Long, val due: Boolean)

    private val lock = Any()
    private var pending: Pending? = null
    private var prefs: SharedPreferences? = null

    private enum class Attempt { DONE, UNREADABLE }

    /**
     * Wires the policy to the process: loads the pending stamp a killed process left, checks it at every
     * foreground (the first one is the app start) and clears Encly's clip whenever [vaultLocked] turns true.
     * Call once from `Application.onCreate`, on the main thread.
     */
    fun install(context: Context, vaultLocked: StateFlow<Boolean>, scope: CoroutineScope) {
        val app = context.applicationContext
        val clipboard = app.getSystemService(ClipboardManager::class.java) ?: return
        val stored = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            prefs = stored
            if (pending == null && stored.contains(KEY_STAMP)) {
                pending = Pending(
                    stamp = stored.getLong(KEY_STAMP, 0L),
                    copiedAt = stored.getLong(KEY_COPIED_AT, 0L),
                    due = stored.getBoolean(KEY_DUE, false),
                )
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = resume(clipboard)
            },
        )
        scope.launch { vaultLocked.collect { if (it) onVaultLocked(clipboard) } }
    }

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
     * Clears the clipboard after [CLEAR_AFTER_MS] if it still holds the clip just set (see the class comment for how
     * it is recognised and what happens when the system hides it). Called right after the clip was set, while the
     * app is in front; a clip whose stamp cannot be read then is not tracked and not cleared.
     */
    fun scheduleClear(clipboard: ClipboardManager) {
        val stamp = timestampOf(clipboard) ?: return
        persist(Pending(stamp, System.currentTimeMillis(), due = false))
        handler.postDelayed({ attempt(clipboard, stamp) }, CLEAR_AFTER_MS)
    }

    /**
     * The process came to the foreground (or started): clears Encly's pending clip if it is due, that is when its
     * minute is over or the vault locked meanwhile; otherwise schedules the rest of the minute.
     */
    internal fun resume(clipboard: ClipboardManager) {
        val current = synchronized(lock) { pending } ?: return
        val age = System.currentTimeMillis() - current.copiedAt
        val remaining = if (current.due || age < 0) 0L else CLEAR_AFTER_MS - age
        if (remaining > 0) {
            handler.postDelayed({ attemptWithRetries(clipboard, current.stamp, FOREGROUND_RETRIES) }, remaining)
        } else {
            attemptWithRetries(clipboard, current.stamp, FOREGROUND_RETRIES)
        }
    }

    /**
     * The vault locked: Encly's pending clip is due at once. Cleared now when the clipboard can be read; otherwise
     * (the app is in the background) at the next foreground.
     */
    internal fun onVaultLocked(clipboard: ClipboardManager) {
        val current = synchronized(lock) {
            val p = pending ?: return
            Pending(p.stamp, p.copiedAt, due = true).also { persist(it) }
        }
        attempt(clipboard, current.stamp)
    }

    private fun attemptWithRetries(clipboard: ClipboardManager, stamp: Long, triesLeft: Int) {
        if (attempt(clipboard, stamp) == Attempt.UNREADABLE && triesLeft > 0) {
            handler.postDelayed({ attemptWithRetries(clipboard, stamp, triesLeft - 1) }, FOREGROUND_RETRY_MS)
        }
    }

    /** Clears the clipboard if it carries [stamp]; a clipboard that cannot be read is left alone and stays pending. */
    private fun attempt(clipboard: ClipboardManager, stamp: Long): Attempt {
        val current = timestampOf(clipboard) ?: return Attempt.UNREADABLE
        if (current == stamp) clear(clipboard)
        forget(stamp)
        return Attempt.DONE
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

    private fun persist(entry: Pending) = synchronized(lock) {
        pending = entry
        prefs?.edit()
            ?.putLong(KEY_STAMP, entry.stamp)
            ?.putLong(KEY_COPIED_AT, entry.copiedAt)
            ?.putBoolean(KEY_DUE, entry.due)
            ?.apply()
    }

    /** Drops the pending record if it is the one for [stamp]; a newer copy keeps its own. */
    private fun forget(stamp: Long) = synchronized(lock) {
        if (pending?.stamp != stamp) return@synchronized
        pending = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun timestampOf(clipboard: ClipboardManager): Long? = try {
        clipboard.primaryClipDescription?.timestamp
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * A [Clipboard] that applies [SensitiveClip] to everything set through it. It must be an
 * [AndroidClipboard]: Compose's text toolbar reads the system clipboard through
 * `Clipboard.nativeClipboardManager`, which throws for any other [Clipboard], so a plain
 * wrapper crashed the app on every text selection.
 */
private class SensitiveComposeClipboard(private val delegate: Clipboard) : AndroidClipboard {
    override val clipboardManager: ClipboardManager get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry?.clipData?.let(SensitiveClip::markSensitive)
        delegate.setClipEntry(clipEntry)
        if (clipEntry != null) SensitiveClip.scheduleClear(clipboardManager)
    }
}

/** Routes every Compose copy under [content] through [SensitiveClip]. */
@Composable
fun SensitiveClipboardBoundary(content: @Composable () -> Unit) {
    val platform = LocalClipboard.current
    val clipboard = remember(platform) { SensitiveComposeClipboard(platform) }
    CompositionLocalProvider(LocalClipboard provides clipboard, content = content)
}
