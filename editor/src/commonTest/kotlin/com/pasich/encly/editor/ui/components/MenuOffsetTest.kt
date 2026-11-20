package com.pasich.encly.editor.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a right click's menu hangs from, in either layout direction. */
class MenuOffsetTest {
    @Test
    fun aLeftToRightMenuIsOffsetFromTheLeftEdge() {
        assertEquals(30f, menuOffsetFromStart(x = 30f, width = 400, rtl = false))
    }

    @Test
    fun aRightToLeftMenuIsOffsetFromTheRightEdge() {
        assertEquals(370f, menuOffsetFromStart(x = 30f, width = 400, rtl = true))
        assertEquals(0f, menuOffsetFromStart(x = 400f, width = 400, rtl = true))
    }
}
