package com.pasich.encly.desktop

import com.pasich.encly.core.keystore.DesktopPasswordPolicy
import com.pasich.encly.core.security.PinSecretPolicy
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.platform.DesktopEnvironment
import dev.zacsweers.metro.createGraphFactory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The desktop graph binds the policy of its environment and not the PIN policy that `:core:security` contributes to
 * every graph; the environment of the app (`DesktopEnvironment.system`) takes the password policy.
 */
class SecretPolicyBindingTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun graphWith(environment: DesktopEnvironment): DesktopAppGraph =
        createGraphFactory<DesktopAppGraph.Factory>().create(environment)

    @Test
    fun anEnvironmentThatNamesNoPolicyIsTheOneOfTheApp() {
        val plain = testEnvironment(temp.root.toPath())
        val production = DesktopEnvironment(
            os = plain.os,
            dataDirectory = plain.dataDirectory,
            pinFactor = plain.pinFactor,
            lockSignals = plain.lockSignals,
            bootClock = plain.bootClock,
            captureExclusion = plain.captureExclusion,
            fileDialogs = plain.fileDialogs,
            linkOpener = plain.linkOpener,
            systemClipboard = plain.systemClipboard,
        )

        assertIs<DesktopPasswordPolicy>(production.secretPolicy)
    }

    @Test
    fun theGraphOfTheAppAsksForAPasswordAndOffersFiveWords() {
        val environment = testEnvironment(temp.root.toPath(), secretPolicy = DesktopPasswordPolicy())
        val security = graphWith(environment).securityManager

        assertEquals(SecretKind.PASSWORD, security.secretKind)
        assertEquals(768, security.maxTypedUnits)
        // A PIN is no password: the digits the Android keypad takes are refused here, with the reason.
        assertEquals(SecretRule.TOO_SHORT, security.checkNewSecret("123456".toCharArray()))
        assertEquals(SecretRule.TOO_SIMPLE, security.checkNewSecret("123456789012".toCharArray()))
        assertEquals(SecretRule.OK, security.checkNewSecret("correct horse battery".toCharArray()))
        val suggestion = assertNotNull(security.suggestSecret())
        assertEquals(SecretRule.OK, security.checkNewSecret(suggestion))
        assertEquals(5, String(suggestion).split(' ').size)
    }

    @Test
    fun aGraphGivenThePinPolicyKeepsThePinAndTheContributedBindingIsNotAlsoThere() {
        val environment = testEnvironment(temp.root.toPath(), secretPolicy = PinSecretPolicy())
        val security = graphWith(environment).securityManager

        assertEquals(SecretKind.PIN, security.secretKind)
        assertEquals(12, security.maxTypedUnits)
        assertEquals(SecretRule.OK, security.checkNewSecret("123456".toCharArray()))
        assertEquals(SecretRule.INVALID, security.checkNewSecret("correct horse battery".toCharArray()))
        assertNull(security.suggestSecret())
    }
}
