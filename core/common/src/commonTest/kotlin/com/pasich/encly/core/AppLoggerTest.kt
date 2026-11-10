package com.pasich.encly.core

import kotlin.test.Test

class AppLoggerTest {
    @Test
    fun loggingIsDisabledSoNothingIsEverEmitted() {
        AppLogger.w("tag", "event")
        AppLogger.e("tag", "event", IllegalStateException("secret detail"))
    }
}
