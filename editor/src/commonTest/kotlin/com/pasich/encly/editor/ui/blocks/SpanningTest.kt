package com.pasich.encly.editor.ui.blocks

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpanningTest {
    @Test
    fun aMatchOnOneLineSpansFromTheFirstBoxToTheLast() {
        assertEquals(Rect(10f, 0f, 50f, 20f), spanning(Rect(10f, 0f, 20f, 20f), Rect(40f, 0f, 50f, 20f)))
    }

    @Test
    fun aMatchThatWrapsHasANonNegativeSize() {
        val wrapped = spanning(Rect(300f, 0f, 320f, 20f), Rect(0f, 20f, 40f, 40f))

        assertEquals(Rect(0f, 0f, 320f, 40f), wrapped)
        assertTrue(wrapped.width > 0 && wrapped.height > 0)
    }
}
