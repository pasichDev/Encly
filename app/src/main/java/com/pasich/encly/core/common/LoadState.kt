package com.pasich.encly.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * What a list screen shows while its data is read. [Failed] is a state of its own: a screen
 * renders it as an error, never as the "nothing here" empty state, so a read failure cannot
 * look like an empty (or wiped) vault.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val error: LoadError) : LoadState<Nothing>
}

/** A read failure as the user sees it. Never carries `Throwable.message`. */
data class LoadError(val title: UiText, val message: UiText)

/** The loaded value, or null while loading or after a failure. */
fun <T> LoadState<T>.valueOrNull(): T? = (this as? LoadState.Ready)?.value

/**
 * The state after [next] arrives, except that a reload keeps showing the data already loaded
 * until the new data (or a failure) replaces it, instead of blinking back to a placeholder.
 */
fun <T> LoadState<T>.reloadWith(next: LoadState<T>): LoadState<T> = if (next is LoadState.Loading &&
    this is LoadState.Ready
) {
    this
} else {
    next
}

/**
 * Wraps a data flow for a screen: [LoadState.Loading] first, then every value as
 * [LoadState.Ready], and an upstream failure as [LoadState.Failed] with [error].
 *
 * `catch` only sees upstream exceptions, so a collector's own exception is never turned into
 * an error state, and cancellation always propagates.
 */
fun <T> Flow<T>.asLoadState(error: LoadError): Flow<LoadState<T>> = map<T, LoadState<T>> { LoadState.Ready(it) }
    .onStart { emit(LoadState.Loading) }
    .catch { cause ->
        if (cause is CancellationException) throw cause
        emit(LoadState.Failed(error))
    }
