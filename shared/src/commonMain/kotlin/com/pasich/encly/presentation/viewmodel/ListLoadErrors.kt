package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadError
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.notes_load_failed_desc
import com.pasich.encly.core.resources.notes_load_failed_title
import com.pasich.encly.core.resources.tags_load_failed_title

/** The read errors the list screens show. */
internal object ListLoadErrors {
    val NOTES = LoadError(
        title = UiText.of(Res.string.notes_load_failed_title),
        message = UiText.of(Res.string.notes_load_failed_desc),
    )

    val TAGS = LoadError(
        title = UiText.of(Res.string.tags_load_failed_title),
        message = UiText.of(Res.string.notes_load_failed_desc),
    )
}
