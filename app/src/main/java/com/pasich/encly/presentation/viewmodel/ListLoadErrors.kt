package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.R
import com.pasich.encly.core.common.LoadError
import com.pasich.encly.core.common.UiText

/** The read errors the list screens show. */
internal object ListLoadErrors {
    val NOTES = LoadError(
        title = UiText.of(R.string.notes_load_failed_title),
        message = UiText.of(R.string.notes_load_failed_desc),
    )

    val TAGS = LoadError(
        title = UiText.of(R.string.tags_load_failed_title),
        message = UiText.of(R.string.notes_load_failed_desc),
    )
}
