package com.pasich.encly.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

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
 * Injected instead of referencing [Dispatchers.IO] directly so unit tests can run that work on
 * their test dispatcher: a real I/O thread outlives the test and resumes onto a
 * Dispatchers.Main that was already reset.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Provides coroutine infrastructure that lives for the whole application process. */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * A [SupervisorJob]-backed scope on [Dispatchers.IO]. A failure in one child does
     * not cancel the others, and the scope is never cancelled (it is tied to the
     * application process), so work launched here survives ViewModel teardown.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
