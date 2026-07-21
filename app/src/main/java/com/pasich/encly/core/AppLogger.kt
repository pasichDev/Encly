package com.pasich.encly.core

import com.pasich.encly.core.AppLogger
import net.sqlcipher.BuildConfig

/**
 * Central application logger.
 *
 * All logging goes through here so it can be filtered in one place: messages are
 * emitted only in debug builds (release is additionally stripped by R8). Keep tags
 * short and messages in English.
 */
object AppLogger {

    private val enabled: Boolean = BuildConfig.DEBUG

    fun v(tag: String, message: String) {
        if (enabled) AppLogger.v(tag, message)
    }

    fun d(tag: String, message: String) {
        if (enabled) AppLogger.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (enabled) AppLogger.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) AppLogger.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) AppLogger.e(tag, message, throwable)
    }
}
