package com.pasich.encly.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.pasich.encly.data.export.NoteDocument
import kotlinx.io.Sink

/** What came of a print request that was not an error. */
enum class PrintResult {
    /** The job was handed to the printer. */
    PRINTED,

    /** The user closed the print dialog. */
    CANCELLED,
}

/**
 * Turns a [NoteDocument] into a PDF or a printout. The desktop provides one ([LocalNoteDocumentRenderer]); on
 * Android there is none and the screens offer neither action. Both receive plaintext, so a caller reaches
 * them only after the plaintext-export warning and the re-authentication.
 */
interface NoteDocumentRenderer {
    /** Writes [document] as one PDF to [out]. The whole file is built before the first byte is written. */
    fun writePdf(document: NoteDocument, out: Sink)

    /** Shows the system print dialog and prints [document]; throws when printing fails. */
    suspend fun print(document: NoteDocument): PrintResult
}

/** The host's PDF and print support, or null where the host has none (Android). */
val LocalNoteDocumentRenderer = staticCompositionLocalOf<NoteDocumentRenderer?> { null }

/** What a host with several windows lets a note do ("Open in new window"). */
fun interface NoteWindows {
    /** Opens a window showing note [noteId]. */
    fun openNote(noteId: Long)
}

/** The host's window opener, or null where the host has one window only (Android). */
val LocalNoteWindows = staticCompositionLocalOf<NoteWindows?> { null }

/**
 * Which window edits which note. A note has one editor at a time across the windows of a host: the editor
 * saves the whole note, so a second editor would overwrite the first one's work with what it loaded earlier.
 * Windows are told apart by the key they hold (compared by equality); a window may claim a note more than once
 * (an editor replaced by another of the same note), and a claim of another window refuses.
 */
class NoteEditRegistry {
    private val holders = HashMap<Long, Holder>()

    private class Holder(val window: Any, var count: Int)

    /** The claims of one window. */
    fun forWindow(window: Any): NoteEditClaims = NoteEditClaims(this, window)

    /** Claims [noteId] for [window]; false when another window holds it. */
    @Synchronized
    internal fun claim(window: Any, noteId: Long): Boolean {
        val holder = holders[noteId]
        return when {
            holder == null -> {
                holders[noteId] = Holder(window, 1)
                true
            }

            holder.window == window -> {
                holder.count++
                true
            }

            else -> false
        }
    }

    /** Gives back one claim of [window] on [noteId]. */
    @Synchronized
    internal fun release(window: Any, noteId: Long) {
        val holder = holders[noteId] ?: return
        if (holder.window != window) return
        if (--holder.count <= 0) holders.remove(noteId)
    }

    /** Whether any window edits [noteId]. */
    @Synchronized
    fun isClaimed(noteId: Long): Boolean = holders.containsKey(noteId)
}

/** The claims of one window of a [NoteEditRegistry]. */
class NoteEditClaims internal constructor(private val registry: NoteEditRegistry, private val window: Any) {
    /** True when this window may edit [noteId] (and now holds a claim it must [release]). */
    fun claim(noteId: Long): Boolean = registry.claim(window, noteId)

    fun release(noteId: Long) = registry.release(window, noteId)
}

/** This window's note claims, or null where the host has one window only (Android): editors never refuse. */
val LocalNoteEditClaims = staticCompositionLocalOf<NoteEditClaims?> { null }

/**
 * True in a host whose several windows share one vault and one lock: a lock screen then follows an unlock made on
 * another window's lock screen. False (Android: one window) leaves the lock screen to its own unlock only.
 */
val LocalSessionSharedAcrossWindows = staticCompositionLocalOf { false }
