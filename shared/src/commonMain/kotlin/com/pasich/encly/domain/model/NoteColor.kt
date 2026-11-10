package com.pasich.encly.domain.model

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.note_color_blue
import com.pasich.encly.core.resources.note_color_green
import com.pasich.encly.core.resources.note_color_none
import com.pasich.encly.core.resources.note_color_orange
import com.pasich.encly.core.resources.note_color_pink
import com.pasich.encly.core.resources.note_color_purple
import com.pasich.encly.core.resources.note_color_red
import com.pasich.encly.core.resources.note_color_teal
import com.pasich.encly.core.resources.note_color_yellow
import com.pasich.encly.data.model.Note
import org.jetbrains.compose.resources.StringResource

/**
 * The colour a note can be marked with. Only the stable [key] is stored (database and backups);
 * the shade is resolved from the active theme at draw time, so it never goes stale when the
 * theme or light/dark mode changes. An unknown key (a newer palette) reads as [NONE].
 */
enum class NoteColor(val key: String, val labelRes: StringResource) {
    NONE(Note.NO_COLOR, Res.string.note_color_none),
    RED("red", Res.string.note_color_red),
    ORANGE("orange", Res.string.note_color_orange),
    YELLOW("yellow", Res.string.note_color_yellow),
    GREEN("green", Res.string.note_color_green),
    TEAL("teal", Res.string.note_color_teal),
    BLUE("blue", Res.string.note_color_blue),
    PURPLE("purple", Res.string.note_color_purple),
    PINK("pink", Res.string.note_color_pink),
    ;

    /** The next colour of the palette; the last one wraps to [NONE]. */
    fun next(): NoteColor = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): NoteColor = entries.firstOrNull { it.key == key } ?: NONE
    }
}
