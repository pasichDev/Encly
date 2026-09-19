package com.pasich.encly.core

/**
 * Central application logger.
 *
 * Security-first beta policy: application logging is intentionally disabled in every build.
 * Notes, tasks, recovery material, database failures and exception messages can contain
 * protected plaintext or metadata that must not cross into logcat.
 *
 * Re-introduce logging only through a structured, explicitly non-sensitive event schema.
 */
@Suppress("UNUSED_PARAMETER")
object AppLogger {
    fun v(tag: String, message: String) = Unit
    fun d(tag: String, message: String) = Unit
    fun i(tag: String, message: String) = Unit
    fun w(tag: String, message: String, throwable: Throwable? = null) = Unit
    fun e(tag: String, message: String, throwable: Throwable? = null) = Unit
}
