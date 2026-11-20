package com.pasich.encly.domain.model

/**
 * A note's own text size and line spacing. Each is null while the note follows the default set in
 * Settings → Appearance. Kept in the app's preferences by note id, not in the vault, so adding it
 * changes no schema.
 */
data class NoteTextOverride(val size: Int? = null, val spacing: NoteLineSpacing? = null) {
    val isSet: Boolean get() = size != null || spacing != null
}
