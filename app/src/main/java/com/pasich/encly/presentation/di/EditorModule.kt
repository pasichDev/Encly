package com.pasich.encly.presentation.di

import com.pasich.encly.R
import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.presentation.editor.persistence.NoteCopyTitle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object EditorModule {

    @Provides
    fun provideNoteCopyTitle(strings: AppStrings): NoteCopyTitle =
        NoteCopyTitle { title -> strings.get(R.string.note_copy_title, title) }
}
