package com.pasich.encly.domain.enums

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.sort_created_asc
import com.pasich.encly.core.resources.sort_created_asc_short
import com.pasich.encly.core.resources.sort_created_desc
import com.pasich.encly.core.resources.sort_created_desc_short
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
    ;

    /** Orders notes the way this option names. */
    fun comparator(): Comparator<Note> = when (this) {
        UPDATED_ASC -> compareBy { it.date }
        UPDATED_DESC -> compareByDescending { it.date }
        CREATED_ASC -> compareBy { it.dateCreate }
        CREATED_DESC -> compareByDescending { it.dateCreate }
    }

    companion object {
        fun fromIndex(index: Int): NoteSortOption = entries.find { it.index == index } ?: UPDATED_DESC
    }
}
