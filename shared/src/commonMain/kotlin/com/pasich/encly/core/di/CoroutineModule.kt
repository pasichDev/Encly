package com.pasich.encly.core.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Qualifier for the application-lifetime [CoroutineScope].
 *
 * Use it for fire-and-forget work that MUST outlive the caller's own scope —
 * e.g. persisting a note during ViewModel.onCleared(), where [viewModelScope] is
 * already cancelled and any coroutine launched in it would be dropped.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Qualifier for the dispatcher that blocking I/O (Room/SQLCipher) runs on.
 *
 * Injected instead of referencing [platformIoDispatcher] directly so unit tests can run that work on
 * their test dispatcher: a real I/O thread outlives the test and resumes onto a
 * Dispatchers.Main that was already reset.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Provides coroutine infrastructure that lives for the whole application process. */
@ContributesTo(AppScope::class)
@BindingContainer
object CoroutineModule {

    /**
     * A [SupervisorJob]-backed scope on [platformIoDispatcher]. A failure in one child does
     * not cancel the others, and the scope is never cancelled (it is tied to the
     * application process), so work launched here survives ViewModel teardown.
     */
    @Provides
    @SingleIn(AppScope::class)
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + platformIoDispatcher)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = platformIoDispatcher
}
