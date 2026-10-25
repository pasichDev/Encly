package com.pasich.encly.domain.model

import androidx.annotation.StringRes
import com.pasich.encly.R
import com.pasich.encly.data.model.Note

/**
 * The colour a note can be marked with. Only the stable [key] is stored (database and backups);
 * the shade is resolved from the active theme at draw time, so it never goes stale when the
 * theme or light/dark mode changes. An unknown key (a newer palette) reads as [NONE].
 */
enum class NoteColor(val key: String, @get:StringRes val labelRes: Int) {
    NONE(Note.NO_COLOR, R.string.note_color_none),
    RED("red", R.string.note_color_red),
    ORANGE("orange", R.string.note_color_orange),
    YELLOW("yellow", R.string.note_color_yellow),
    GREEN("green", R.string.note_color_green),
    TEAL("teal", R.string.note_color_teal),
    BLUE("blue", R.string.note_color_blue),
    PURPLE("purple", R.string.note_color_purple),
    PINK("pink", R.string.note_color_pink),
    ;

    companion object {
        fun fromKey(key: String?): NoteColor = entries.firstOrNull { it.key == key } ?: NONE
    }
}
