package com.pasich.encly.ios.platform

import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.ios.storage.IosKeyValueStore
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The address filter of the external links and the in-app language controller, on the Simulator. */
class IosHostServicesTest {
    private val flagsFile = NSTemporaryDirectory().trimEnd('/') + "/encly-locale-" + NSUUID().UUIDString + ".json"
    private val defaults = NSUserDefaults.standardUserDefaults
    private var savedLanguages: List<*>? = null

    @BeforeTest
    fun rememberAppleLanguages() {
        savedLanguages = defaults.arrayForKey(APPLE_LANGUAGES)
    }

    @AfterTest
    fun restoreAppleLanguages() {
        val saved = savedLanguages
        if (saved == null) defaults.removeObjectForKey(APPLE_LANGUAGES) else defaults.setObject(saved, APPLE_LANGUAGES)
        SystemFileSystem.delete(Path(flagsFile), mustExist = false)
    }

    private fun mayLeave(url: String): Boolean = parseExternalUrl(url)?.let(::mayLeaveApp) == true

    @Test
    fun webAndMailAddressesMayLeaveTheApp() {
        assertTrue(mayLeave("https://example.org/page?q=1"))
        assertTrue(mayLeave("http://example.org"))
        assertTrue(mayLeave("mailto:someone@example.org"))
        assertTrue(mayLeave("  https://example.org  "), "the address is trimmed")
        assertTrue(mayLeave("HTTPS://EXAMPLE.ORG"), "the scheme is not case-sensitive")
    }

    @Test
    fun otherSchemesAreRefused() {
        assertFalse(mayLeave("file:///private/var/mobile/secret.txt"))
        assertFalse(mayLeave("javascript:alert(1)"))
        assertFalse(mayLeave("tel:+10000000000"))
        assertFalse(mayLeave("sms:+10000000000"))
        assertFalse(mayLeave("encly://open"))
        assertFalse(mayLeave("app-settings:"))
    }

    @Test
    fun textWithoutASchemeIsNotAnAddress() {
        assertNull(parseExternalUrl("example.org/page"))
        assertNull(parseExternalUrl(""))
        assertNull(parseExternalUrl("not a url"))
        assertNotNull(parseExternalUrl("https://example.org"))
    }

    @Test
    fun theLanguageIsKeptAndWrittenWhereTheSystemReadsIt() {
        val controller = IosAppLocaleController(IosKeyValueStore(flagsFile))
        assertEquals(AppLanguage.SYSTEM, controller.current())

        controller.apply(AppLanguage.UKRAINIAN)

        assertEquals(AppLanguage.UKRAINIAN, controller.current())
        assertEquals(listOf("uk"), defaults.arrayForKey(APPLE_LANGUAGES))
    }

    @Test
    fun followingTheSystemRemovesTheOverride() {
        val controller = IosAppLocaleController(IosKeyValueStore(flagsFile))
        controller.apply(AppLanguage.GERMAN)

        controller.apply(AppLanguage.SYSTEM)

        assertEquals(AppLanguage.SYSTEM, controller.current())
        assertNull(defaults.arrayForKey(APPLE_LANGUAGES)?.takeIf { it == listOf("de") })
    }

    @Test
    fun everyApplyBumpsTheRevisionAndSyncRewritesTheSystemValue() {
        val controller = IosAppLocaleController(IosKeyValueStore(flagsFile))
        val before = controller.revision.value

        controller.apply(AppLanguage.FRENCH)
        controller.apply(AppLanguage.FRENCH)
        assertEquals(before + 2, controller.revision.value)

        defaults.removeObjectForKey(APPLE_LANGUAGES)
        controller.syncAppleLanguages()
        assertEquals(listOf("fr"), defaults.arrayForKey(APPLE_LANGUAGES))
    }

    private companion object {
        const val APPLE_LANGUAGES = "AppleLanguages"
    }
}
