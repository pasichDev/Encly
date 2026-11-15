package com.pasich.encly.platform

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.faq_db_key_a
import com.pasich.encly.core.resources.faq_db_key_a_desktop
import com.pasich.encly.core.resources.faq_db_key_a_ios
import com.pasich.encly.core.resources.faq_screenshots_a
import com.pasich.encly.core.resources.faq_swipe_a
import com.pasich.encly.core.resources.faq_swipe_a_other
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_pin_key_lost_desktop
import com.pasich.encly.core.resources.lock_pin_key_lost_named
import com.pasich.encly.core.resources.pin_unlock_with_biometric_named
import com.pasich.encly.core.resources.pin_unlock_with_fingerprint
import com.pasich.encly.core.resources.security_encryption_pin_plain
import com.pasich.encly.core.resources.security_encryption_pin_plain_named
import com.pasich.encly.core.resources.security_encryption_pin_plain_nobio
import com.pasich.encly.core.resources.security_protection_pin_desc
import com.pasich.encly.core.resources.security_protection_pin_desc_desktop
import com.pasich.encly.core.resources.security_protection_pin_desc_ios
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which text each host gets: Android keeps the original, iOS names its biometrics, the desktop mentions none. */
class HostTextsTest {
    private val fingerprint = BiometricWording.Fingerprint
    private val faceId = BiometricWording.Named("Face ID")
    private val none = BiometricWording.None

    @Test
    fun androidAlwaysGetsTheOriginalText() {
        listOf(
            Res.string.pin_unlock_with_fingerprint,
            Res.string.security_encryption_pin_plain,
            Res.string.lock_pin_key_lost,
            Res.string.security_protection_pin_desc,
            Res.string.faq_db_key_a,
            Res.string.faq_swipe_a,
            Res.string.faq_screenshots_a,
        ).forEach { assertEquals(it, hostVariant(it, HostPlatform.ANDROID, fingerprint)) }
    }

    @Test
    fun iosNamesItsBiometrics() {
        assertEquals(
            Res.string.pin_unlock_with_biometric_named,
            hostVariant(Res.string.pin_unlock_with_fingerprint, HostPlatform.IOS, faceId),
        )
        assertEquals(
            Res.string.security_encryption_pin_plain_named,
            hostVariant(Res.string.security_encryption_pin_plain, HostPlatform.IOS, faceId),
        )
        assertEquals(
            Res.string.lock_pin_key_lost_named,
            hostVariant(Res.string.lock_pin_key_lost, HostPlatform.IOS, faceId),
        )
    }

    @Test
    fun theProtectionTextsNameTheKeyStoreOfTheHost() {
        assertEquals(
            Res.string.security_protection_pin_desc_ios,
            hostVariant(Res.string.security_protection_pin_desc, HostPlatform.IOS, faceId),
        )
        assertEquals(
            Res.string.security_protection_pin_desc_desktop,
            hostVariant(Res.string.security_protection_pin_desc, HostPlatform.DESKTOP, none),
        )
        assertEquals(Res.string.faq_db_key_a_ios, hostVariant(Res.string.faq_db_key_a, HostPlatform.IOS, faceId))
        assertEquals(Res.string.faq_db_key_a_desktop, hostVariant(Res.string.faq_db_key_a, HostPlatform.DESKTOP, none))
    }

    @Test
    fun theDesktopMentionsNoBiometrics() {
        assertEquals(
            Res.string.security_encryption_pin_plain_nobio,
            hostVariant(Res.string.security_encryption_pin_plain, HostPlatform.DESKTOP, none),
        )
        assertEquals(
            Res.string.lock_pin_key_lost_desktop,
            hostVariant(Res.string.lock_pin_key_lost, HostPlatform.DESKTOP, none),
        )
    }

    @Test
    fun textsOnlyAndroidCanReadAreSwappedElsewhere() {
        assertEquals(Res.string.faq_swipe_a_other, hostVariant(Res.string.faq_swipe_a, HostPlatform.IOS, faceId))
        assertEquals(Res.string.faq_swipe_a_other, hostVariant(Res.string.faq_swipe_a, HostPlatform.DESKTOP, none))
    }
}
