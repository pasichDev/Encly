package com.pasich.encly.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class MainThreadTimerTest {
    @Test
    fun theNullTimerRunsNothingAndItsCancelIsHarmless() {
        var ran = 0

        val cancel = MainThreadTimer.None.schedule(0L) { ran++ }
        cancel()

        assertEquals(0, ran)
    }
}
