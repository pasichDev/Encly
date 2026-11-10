package com.pasich.encly.core.backup

import com.pasich.encly.core.security.AutoLockDelay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupSettingsTest {

    @Test
    fun aKeyIsEitherIncludedOrExcludedNeverBoth() {
        assertEquals(emptySet(), BackupSettings.included.keys intersect BackupSettings.excluded.keys)
    }

    @Test
    fun noSecurityStateIsEverIncluded() {
        // Words (of `snake_case` keys) that mark a credential, a lockout or install state.
        val forbidden = setOf(
            "pin", "biometric", "duress", "decoy", "wipe", "lockout", "failures", "penalty", "slot", "recovery",
            "backup", "onboarding", "screenshot", "screenshots", "dek", "pending", "window", "seen", "version",
        )
        BackupSettings.included.keys.forEach { key ->
            val words = key.split('_', '.').toSet()
            assertEquals(emptySet(), words intersect forbidden, "$key must not be backed up")
        }
    }

    @Test
    fun everyIncludedKeyHasAKindThatAcceptsItsOwnDefault() {
        val defaults = mapOf(
            BackupSettings.IS_DYNAMIC_THEME to "false",
            BackupSettings.THEME_TYPE to "SYSTEM",
            BackupSettings.FONT_SIZE to "16",
            BackupSettings.AUTO_LOCK_DELAY to AutoLockDelay.DEFAULT.name,
            BackupSettings.APP_LANGUAGE to "SYSTEM",
        )
        assertEquals(defaults, BackupSettings.accepted(defaults))
    }

    @Test
    fun autoLockChoicesAreExactlyTheAppsDelays() {
        AutoLockDelay.entries.forEach { delay ->
            assertTrue(BackupSettings.included.getValue(BackupSettings.AUTO_LOCK_DELAY).accepts(delay.name))
        }
    }

    @Test
    fun unknownKeysAndBadValuesAreDroppedOneByOne() {
        val raw = mapOf(
            BackupSettings.THEME_TYPE to "DARK",
            BackupSettings.THEME_PALETTE to "NEON",
            BackupSettings.FONT_SIZE to "9",
            BackupSettings.SHOW_TASKS to "yes",
            BackupSettings.SORT to "CREATED_DESC",
            "auth.biometric_enabled" to "true",
            "pin.slot" to "AAAA",
            "from_the_future" to "1",
        )

        assertEquals(
            mapOf(BackupSettings.THEME_TYPE to "DARK", BackupSettings.SORT to "CREATED_DESC"),
            BackupSettings.accepted(raw),
        )
    }

    @Test
    fun numbersMustBeInRangeAndPlainDigits() {
        val size = BackupSettings.included.getValue(BackupSettings.FONT_SIZE)

        assertTrue(size.accepts("10") && size.accepts("32") && size.accepts("20"))
        assertFalse(size.accepts("33") || size.accepts("9") || size.accepts("") || size.accepts("1e1"))
        assertFalse(size.accepts("20.0") || size.accepts("-12"))
    }

    @Test
    fun aHugeMapIsReadOnlyUpToTheLimit() {
        val raw = (0 until BackupSettings.MAX_ENTRIES + 10).associate { "key_$it" to "x" } +
            mapOf(BackupSettings.SHOW_TASKS to "true")

        // The valid key sits past the limit, so it is not read; nothing throws.
        assertEquals(emptyMap(), BackupSettings.accepted(raw))
    }
}
