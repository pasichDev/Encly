package com.pasich.encly.domain.enums

import androidx.annotation.StringRes
import com.pasich.encly.R

enum class NoteSortOption(
    @param:StringRes val labelRes: Int, @param:StringRes val shortLabelRes: Int, val index: Int
) {
    UPDATED_ASC(
        labelRes = R.string.sort_updated_asc,
        shortLabelRes = R.string.sort_updated_asc_short,
        index = 0
    ),
    UPDATED_DESC(
        labelRes = R.string.sort_updated_desc,
        shortLabelRes = R.string.sort_updated_desc_short,
        index = 1
    ), // DEFAULT
    CREATED_ASC(
        labelRes = R.string.sort_created_asc,
        shortLabelRes = R.string.sort_created_asc_short,
        index = 2
    ),
    CREATED_DESC(
        labelRes = R.string.sort_created_desc,
        shortLabelRes = R.string.sort_created_desc_short,
        index = 3
    );

    companion object {
        fun fromIndex(index: Int): NoteSortOption =
            entries.find { it.index == index } ?: UPDATED_DESC
    }
}
