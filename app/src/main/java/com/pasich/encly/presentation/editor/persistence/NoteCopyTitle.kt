package com.pasich.encly.presentation.editor.persistence

/** The title of a copy of a note titled `title`, in the user's language ("Title (Copy)"). */
fun interface NoteCopyTitle {
    operator fun invoke(title: String): String
}
