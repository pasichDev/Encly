package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * SecurityManager.eraseEpoch, for the editor to record with its note (see [RelockReturn]);
 * MainActivity provides it. Without it nothing is recorded, so no note is reopened.
 */
val LocalEraseEpoch = staticCompositionLocalOf<(() -> Long)?> { null }

/**
 * Brings the user back to the note that was open when the app re-locked in the background.
 *
 * The editor records its note's id on its own back-stack entry ([OPEN_NOTE_ID]), including the
 * id a new note gets on its first save, and the erase epoch of the vault it showed it in
 * ([OPEN_NOTE_EPOCH], see SecurityManager.eraseEpoch). When the session re-locks, MainActivity
 * turns that into a route ([routeFor]) and leaves it on the lock screen's entry
 * ([RETURN_ROUTE], [RETURN_EPOCH]); a PIN or fingerprint unlock opens it again over Home, unless
 * a wipe-PIN erase changed the epoch since (or the process was restarted, which starts another
 * epoch). Only the id travels, never note content.
 */
object RelockReturn {
    const val OPEN_NOTE_ID = "relock_open_note_id"
    const val OPEN_NOTE_EPOCH = "relock_open_note_epoch"
    const val RETURN_ROUTE = "relock_return_route"
    const val RETURN_EPOCH = "relock_return_epoch"

    /**
     * The route that reopens the note shown by the destination [destinationRoute], or null when
     * that is not the editor or its note was never stored ([openNoteId] missing or not > 0).
     */
    fun routeFor(destinationRoute: String?, openNoteId: Long?, readOnly: Boolean): String? {
        val inEditor = destinationRoute?.startsWith("${NavRoutes.EditNoteRoute.name}/") == true
        val id = openNoteId?.takeIf { it > 0L }
        val params = if (readOnly) "?isReadTrashOnly=true" else ""
        return if (inEditor && id != null) "${NavRoutes.EditNoteRoute.name}/$id$params" else null
    }
}

/** What MainActivity does when it comes back to the foreground (see [afterStop]). */
enum class SessionResume {
    /** Nothing changed that it did not see: its screens are current. */
    KEEP,

    /** The vault was unlocked elsewhere and is closed again: the lock screen, remembering nothing. */
    LOCK_SCREEN,

    /** The vault was unlocked elsewhere and is open: a fresh Home, none of the old screens. */
    HOME,
    ;

    companion object {
        /**
         * [stoppedAt] is the session generation (SessionLockManager.sessionGeneration) when the
         * activity stopped, null when it has not stopped yet. An unlock published meanwhile came
         * from another lock screen (the My Notes hand-off): the screens kept here belong to a
         * session that ended (their ViewModels dropped their content), possibly in a vault that
         * was erased since, so none of them is shown again, nor reopened after the next unlock.
         * A re-lock alone is handled where it is observed, keeping the note to return to.
         */
        fun afterStop(stoppedAt: Long?, generation: Long, locked: Boolean): SessionResume = when {
            stoppedAt == null || stoppedAt == generation -> KEEP
            locked -> LOCK_SCREEN
            else -> HOME
        }
    }
}
