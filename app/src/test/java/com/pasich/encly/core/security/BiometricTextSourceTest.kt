package com.pasich.encly.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prompt texts are never read on the caller's thread, and never empty. */
class BiometricTextSourceTest {
    private fun BiometricTexts.allTexts() = listOf(
        unavailable,
        cancel,
        unlock.title,
        unlock.subtitle,
        unlock.negativeButtonText.orEmpty(),
        masterKey.title,
        masterKey.subtitle,
        settings.title,
        settings.subtitle,
        general.title,
        general.subtitle,
    )

    @Test
    fun theDefaultTextsHaveATitleAndButtonsForEveryPrompt() {
        assertTrue(BiometricTexts.Default.allTexts().all { it.isNotBlank() })
    }

    @Test
    fun beforeAnyRefreshACallerGetsUsableTextsAtOnce() {
        val source = BiometricTextSource()

        val texts = source.current()

        assertTrue(texts.allTexts().all { it.isNotBlank() })
    }

    @Test
    fun textsSetByTheCallerAreReturnedAsTheyAre() {
        val source = BiometricTextSource()
        val prompt = BiometricManager.PromptConfig("title", "subtitle")
        val own = BiometricTexts("unavailable", "cancel", prompt, prompt, prompt, prompt)

        source.set(own)

        assertSame(own, source.current())
        assertEquals("cancel", source.current().cancel)
    }
}
