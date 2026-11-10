package com.pasich.encly.core.keystore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DpapiErrorsTest {
    @Test
    fun aDamagedBlobIsFinalWhicheverWayJnaSpellsTheCode() {
        // ERROR_INVALID_DATA arrives from JNA as HRESULT_FROM_WIN32(13) = 0x8007000D.
        assertTrue(DpapiErrors.isFinal(13))
        assertTrue(DpapiErrors.isFinal(0x8007000D.toInt()))
        assertTrue(DpapiErrors.isFinal(0x80090005.toInt()))
    }

    @Test
    fun aBadOrMissingKeyOrAnUnreadableVersionIsFinal() {
        assertTrue(DpapiErrors.isFinal(0x80090003.toInt()))
        assertTrue(DpapiErrors.isFinal(0x8009000B.toInt()))
        assertTrue(DpapiErrors.isFinal(0x8009000D.toInt()))
        assertTrue(DpapiErrors.isFinal(0x80090006.toInt()))
    }

    @Test
    fun aFailureThatMayPassIsNotFinal() {
        // ERROR_ACCESS_DENIED, ERROR_NOT_ENOUGH_MEMORY, ERROR_GEN_FAILURE, RPC_S_SERVER_UNAVAILABLE, in both spellings.
        for (code in listOf(5, 8, 31, 1722)) {
            assertFalse(DpapiErrors.isFinal(code), "win32 $code")
            assertFalse(DpapiErrors.isFinal(0x80070000.toInt() or code), "hresult $code")
        }
        // ERROR_PROFILE_NOT_FOUND-like and unknown HRESULTs.
        assertFalse(DpapiErrors.isFinal(0x80090020.toInt()))
        assertFalse(DpapiErrors.isFinal(0))
    }

    @Test
    fun theWin32CodeIsTakenOutOfItsHresultOnly() {
        assertEquals(13, DpapiErrors.win32Code(0x8007000D.toInt()))
        assertEquals(13, DpapiErrors.win32Code(13))
        assertEquals(0x80090005.toInt(), DpapiErrors.win32Code(0x80090005.toInt()))
    }

    @Test
    fun aCodeIsDescribedInHex() {
        assertEquals("0x8007000D", DpapiErrors.describe(0x8007000D.toInt()))
        assertEquals("0xD", DpapiErrors.describe(13))
    }
}
