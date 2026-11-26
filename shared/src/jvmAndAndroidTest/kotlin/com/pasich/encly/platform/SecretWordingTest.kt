package com.pasich.encly.platform

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_passphrase_key_lost
import com.pasich.encly.core.resources.lock_passphrase_subtitle
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.security.SecretKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which text each kind of secret gets: a PIN keeps every original, a passphrase the twin, and the host comes after. */
class SecretWordingTest {
    @Test
    fun aPinGetsEveryOriginalText() {
        PassphraseTwins.keys.forEach { assertEquals(it, secretVariant(it, SecretKind.PIN)) }
    }

    @Test
    fun aPassphraseGetsTheTwinOfEveryTextThatNamesThePin() {
        assertTrue(PassphraseTwins.isNotEmpty())
        PassphraseTwins.forEach { (source, twin) ->
            assertEquals(twin, secretVariant(source, SecretKind.PASSWORD))
            assertNotEquals(source, twin)
        }
    }

    @Test
    fun aTextThatDoesNotNameTheSecretReadsTheSame() {
        val recovery = Res.string.lock_use_recovery_phrase

        assertEquals(recovery, secretVariant(recovery, SecretKind.PASSWORD))
    }

    @Test
    fun theTwinsAreNotMappedAgainSoNoTwinIsLostToAHostVariant() {
        PassphraseTwins.values.forEach { twin ->
            assertEquals(twin, secretVariant(twin, SecretKind.PASSWORD))
        }
    }

    @Test
    fun theDesktopTwinWinsOverTheHostVariantOfThePinText() {
        // The PIN text has a desktop sibling; the passphrase text is written for the desktop and keeps its own.
        val twin = secretVariant(Res.string.lock_pin_key_lost, SecretKind.PASSWORD)

        assertEquals(Res.string.lock_passphrase_key_lost, twin)
        assertEquals(twin, hostVariant(twin, HostPlatform.DESKTOP, BiometricWording.None))
        assertEquals(Res.string.lock_passphrase_subtitle, secretVariant(Res.string.lock_subtitle, SecretKind.PASSWORD))
    }
}
