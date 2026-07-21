package com.pasich.encly.core

import android.util.Log

/**
 * Central application logger.
 *
 * All logging goes through here so it can be filtered in one place: messages are
 * emitted only in debug builds (release is additionally stripped by R8). Keep tags
 * short and messages in English.
 *
 * Note: [com.pasich.encly.BuildConfig] is referenced fully-qualified on purpose — an
 * unqualified `BuildConfig` import gets auto-resolved to the SQLCipher library's
 * BuildConfig (whose DEBUG is always false), which would silently disable logging.
 */
object AppLogger {

    private val enabled: Boolean = com.pasich.encly.BuildConfig.DEBUG

    fun v(tag: String, message: String) {
        if (enabled) Log.v(tag, message)
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (enabled) Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.e(tag, message, throwable)
    }
}
