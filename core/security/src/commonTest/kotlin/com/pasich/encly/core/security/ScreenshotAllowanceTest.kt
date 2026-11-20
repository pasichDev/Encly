package com.pasich.encly.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenshotAllowanceTest {
    private val allowance = ScreenshotAllowance()

    @Test
    fun screenshotsAreNotAllowedByDefault() {
        assertFalse(allowance.allowed.value)
        assertFalse(allowance.capturable)
    }

    @Test
    fun allowingMakesTheWindowCapturableUntilRevoked() {
        allowance.allow()
        assertTrue(allowance.allowed.value)
        assertTrue(allowance.capturable)

        allowance.revoke()
        assertFalse(allowance.allowed.value)
        assertFalse(allowance.capturable)
    }

    @Test
    fun aScreenWithASecretStaysProtectedWhileScreenshotsAreAllowed() {
        allowance.allow()

        val release = allowance.protect()

        assertTrue(allowance.allowed.value)
        assertFalse(allowance.capturable)
        release()
        assertTrue(allowance.capturable)
    }

    @Test
    fun theWindowIsCapturableAgainOnlyWhenEverySecretScreenIsGone() {
        allowance.allow()
        val pin = allowance.protect()
        val phrase = allowance.protect()

        pin()
        assertFalse(allowance.capturable)
        phrase()
        assertTrue(allowance.capturable)
    }

    @Test
    fun releasingTwiceDoesNotUndoAnotherScreensProtection() {
        allowance.allow()
        val first = allowance.protect()
        allowance.protect()

        first()
        first()

        assertFalse(allowance.capturable)
    }

    @Test
    fun theWindowIsToldAtOnceAboutEveryChangeAndOnlyChanges() {
        val seen = mutableListOf<Boolean>()
        val stop = allowance.observe { seen += it }

        allowance.allow()
        allowance.allow()
        val release = allowance.protect()
        release()
        allowance.revoke()
        stop()
        allowance.allow()

        assertEquals(listOf(false, true, false, true, false), seen)
    }

    @Test
    fun aLateObserverIsToldTheCurrentState() {
        allowance.allow()
        val seen = mutableListOf<Boolean>()

        allowance.observe { seen += it }

        assertEquals(listOf(true), seen)
    }

    @Test
    fun revokingWhileASecretScreenShowsIsNotAChangeForTheWindow() {
        allowance.allow()
        allowance.protect()
        val seen = mutableListOf<Boolean>()
        allowance.observe { seen += it }

        allowance.revoke()

        assertEquals(listOf(false), seen)
    }
}
