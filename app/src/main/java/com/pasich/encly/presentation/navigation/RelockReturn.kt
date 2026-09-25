package com.pasich.encly.presentation.navigation

/**
 * Brings the user back to the note that was open when the app re-locked in the background.
 *
 * The editor records its note's id on its own back-stack entry ([OPEN_NOTE_ID]), including the
 * id a new note gets on its first save. When the session re-locks, MainActivity turns that
 * into a route ([routeFor]) and leaves it on the lock screen's entry ([RETURN_ROUTE]); a PIN
 * or fingerprint unlock opens it again over Home. Only the id travels, never note content.
 */
object RelockReturn {
    const val OPEN_NOTE_ID = "relock_open_note_id"
    const val RETURN_ROUTE = "relock_return_route"

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
