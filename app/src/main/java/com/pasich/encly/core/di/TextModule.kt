package com.pasich.encly.core.di

import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.core.common.ComposeAppStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object TextModule {

    /** Stored text is resolved by the Compose resources, in the in-app language. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppStrings(): AppStrings = ComposeAppStrings()
}
