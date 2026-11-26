package com.pasich.encly.ui.screens

import com.pasich.encly.platform.PassphraseTwins
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words of the passphrase texts: none says PIN, none mixes the passphrase up with the 12-word recovery phrase, and
 * a text that spoke of the recovery phrase still does.
 */
class SecretWordingTextsTest : ComposeScreenTest() {
    private val pinWord = Regex("""\bPINs?\b""")

    @Test
    fun noPassphraseTextNamesAPin() {
        PassphraseTwins.values.forEach { twin ->
            assertFalse("${str(twin)} still says PIN", pinWord.containsMatchIn(str(twin)))
        }
    }

    @Test
    fun thePassphraseIsNeverCalledTheRecoveryPhraseAndTheRecoveryPhraseKeepsItsName() {
        PassphraseTwins.forEach { (source, twin) ->
            val text = str(twin)
            assertFalse(text, text.contains("recovery passphrase", ignoreCase = true))
            assertFalse(text, text.contains("passphrase phrase", ignoreCase = true))
            if (str(source).contains("recovery phrase")) {
                assertTrue("$text lost the recovery phrase", text.contains("recovery phrase"))
            }
        }
    }
}
