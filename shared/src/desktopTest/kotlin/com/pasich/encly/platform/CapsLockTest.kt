package com.pasich.encly.platform

import java.awt.HeadlessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The AWT Caps Lock state: an answer or "cannot tell", never a crash (a headless or Wayland session refuses). */
class CapsLockTest {
    @Test
    fun theStateIsWhatTheSystemSays() {
        assertEquals(true, capsLockFrom { true })
        assertEquals(false, capsLockFrom { false })
    }

    @Test
    fun aSystemThatRefusesToSayGivesNull() {
        assertNull(capsLockFrom { throw UnsupportedOperationException("no lock keys here") })
        assertNull(capsLockFrom { throw HeadlessException() })
    }

    @Test
    fun theRealQuestionNeverThrows() {
        val state = capsLockOn()

        assertTrue(state == null || state == true || state == false)
    }
}
