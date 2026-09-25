package com.pasich.encly.core

/**
 * Central application logger, for failure events only.
 *
 * Security-first beta policy: application logging is intentionally disabled in every build.
 * Notes, tasks, recovery material, database failures and exception messages can contain
 * protected plaintext or metadata that must not cross into logcat. There are no debug/trace
 * levels on purpose: call sites pass a fixed event description, never note content or
 * `Throwable.message`. Release builds strip the calls, arguments included
 * (`-assumenosideeffects` in proguard-rules.pro).
 *
 * Re-introduce output only through a structured, explicitly non-sensitive event schema.
 */
@Suppress("UNUSED_PARAMETER")
object AppLogger {
    fun w(tag: String, event: String, throwable: Throwable? = null) = Unit
    fun e(tag: String, event: String, throwable: Throwable? = null) = Unit
}
