package com.pasich.encly.domain.model

import com.pasich.encly.data.model.Note

/**
 * The colour a note can be marked with. Only the stable [key] is stored (database and backups);
 * the shade is resolved from the active theme at draw time, so it never goes stale when the
 * theme or light/dark mode changes. An unknown key (a newer palette) reads as [NONE].
 */
enum class NoteColor(val key: String) {
    NONE(Note.NO_COLOR),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    TEAL("teal"),
    BLUE("blue"),
    PURPLE("purple"),
    PINK("pink"),
    ;

    companion object {
        fun fromKey(key: String?): NoteColor = entries.firstOrNull { it.key == key } ?: NONE
    }
}
