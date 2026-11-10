package com.pasich.encly.data.repository

import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.common.suspendRunCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Runs one database write for a repository: a storage failure (Room, SQLCipher, a vault closed
 * by the background re-lock) becomes [Result.failure] instead of reaching the UI as an
 * exception. Cancellation is rethrown. [operation] is a fixed event name, never data.
 */
internal suspend fun <T> storageWrite(tag: String, operation: String, block: suspend () -> T): Result<T> =
    suspendRunCatching { block() }.onFailure { AppLogger.e(tag, "$operation failed") }

/** Fails the write when Room reports that [expected] rows were not all affected. */
internal fun Int.requireRows(expected: Int = 1) {
    check(this >= expected) { "Expected $expected affected rows, got $this" }
}

/**
 * A database flow resolved when it is collected, not when it is requested: a locked or closed
 * vault then fails inside the flow, where the screen's error handling sees it.
 */
internal fun <T> daoFlow(source: () -> Flow<T>): Flow<T> = flow { emitAll(source()) }
