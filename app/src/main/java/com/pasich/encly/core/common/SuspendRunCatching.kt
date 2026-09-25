package com.pasich.encly.core.common

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspending code: any failure of [block] becomes [Result.failure], except
 * [CancellationException], which is rethrown so a cancelled coroutine stays cancelled.
 */
@Suppress("TooGenericExceptionCaught") // The single place storage failures become a Result.
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (e: Exception) {
    Result.failure(e)
}
