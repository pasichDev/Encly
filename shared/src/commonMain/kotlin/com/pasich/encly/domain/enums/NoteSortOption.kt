package com.pasich.encly.domain.enums

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.sort_created_asc
import com.pasich.encly.core.resources.sort_created_asc_short
import com.pasich.encly.core.resources.sort_created_desc
import com.pasich.encly.core.resources.sort_created_desc_short
import com.pasich.encly.core.resources.sort_title_asc
import com.pasich.encly.core.resources.sort_title_asc_short
import com.pasich.encly.core.resources.sort_title_desc
import com.pasich.encly.core.resources.sort_title_desc_short
import com.pasich.encly.core.resources.sort_updated_asc
import com.pasich.encly.core.resources.sort_updated_asc_short
import com.pasich.encly.core.resources.sort_updated_desc
import com.pasich.encly.core.resources.sort_updated_desc_short
import com.pasich.encly.data.model.Note
import org.jetbrains.compose.resources.StringResource

enum class NoteSortOption(val labelRes: StringResource, val shortLabelRes: StringResource, val index: Int) {
    UPDATED_ASC(
        labelRes = Res.string.sort_updated_asc,
        shortLabelRes = Res.string.sort_updated_asc_short,
        index = 0,
    ),
    UPDATED_DESC(
        labelRes = Res.string.sort_updated_desc,
        shortLabelRes = Res.string.sort_updated_desc_short,
        index = 1,
    ), // DEFAULT
    CREATED_ASC(
        labelRes = Res.string.sort_created_asc,
        shortLabelRes = Res.string.sort_created_asc_short,
        index = 2,
    ),
    CREATED_DESC(
        labelRes = Res.string.sort_created_desc,
        shortLabelRes = Res.string.sort_created_desc_short,
        index = 3,
    ),
    TITLE_ASC(
        labelRes = Res.string.sort_title_asc,
        shortLabelRes = Res.string.sort_title_asc_short,
        index = 4,
    ),
    TITLE_DESC(
        labelRes = Res.string.sort_title_desc,
        shortLabelRes = Res.string.sort_title_desc_short,
        index = 5,
    ),
    ;

    /** Orders notes the way this option names. */
    fun comparator(): Comparator<Note> = when (this) {
        UPDATED_ASC -> compareBy { it.date }
        UPDATED_DESC -> compareByDescending { it.date }
        CREATED_ASC -> compareBy { it.createdOrEdited() }
        CREATED_DESC -> compareByDescending { it.createdOrEdited() }
        TITLE_ASC -> titleComparator(descending = false)
        TITLE_DESC -> titleComparator(descending = true)
    }

    /**
     * Titles by the language's alphabet, case-insensitive ([titleCollator]); equal titles by the
     * last edit, newest first. A note without a title (blank) always comes after the titled ones in
     * both directions, and those are ordered by the last edit, newest first: their text is no title,
     * and the list has no better stable key for them than the date.
     */
    private fun titleComparator(descending: Boolean): Comparator<Note> {
        val collator = titleCollator()
        return Comparator { a, b ->
            val aTitle = a.title.trim()
            val bTitle = b.title.trim()
            when {
                aTitle.isEmpty() && bTitle.isEmpty() -> b.date.compareTo(a.date)

                aTitle.isEmpty() -> 1

                bTitle.isEmpty() -> -1

                else -> {
                    val byTitle = collator.compare(aTitle, bTitle).let { if (descending) -it else it }
                    if (byTitle != 0) byTitle else b.date.compareTo(a.date)
                }
            }
        }
    }

    /**
     * The creation time, or for a note that lost it (an autosave bug stored 0) the only time still
     * known, when it was last edited. Never an invented one (an import does the same: created, else
     * edited). Without it those notes would all sort as the oldest, ahead of every dated note. Such a
     * note moves with its edits in the creation orders; its real creation time cannot be recovered.
     */
    private fun Note.createdOrEdited(): Long = if (dateCreate > 0L) dateCreate else date

    companion object {
        fun fromIndex(index: Int): NoteSortOption = entries.find { it.index == index } ?: UPDATED_DESC
    }
}
