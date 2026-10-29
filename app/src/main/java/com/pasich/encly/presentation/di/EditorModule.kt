package com.pasich.encly.presentation.di

import com.pasich.encly.R
import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.presentation.editor.persistence.NoteCopyTitle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
object EditorModule {

    @Provides
    fun provideNoteCopyTitle(strings: AppStrings): NoteCopyTitle = NoteCopyTitle { title ->
        strings.get(R.string.note_copy_title, title.ifBlank { strings.get(R.string.untitled) })
    }
}
