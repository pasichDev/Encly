package com.pasich.encly.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun noTagMeansSystemDefault() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
    }

    @Test
    fun matchesByPrimaryLanguageSubtag() {
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTag("uk"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTag("uk-UA"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt-BR"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de_AT"))
        assertEquals(AppLanguage.DUTCH, AppLanguage.fromTag("NL"))
    }

    @Test
    fun unsupportedLanguageFallsBackToSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("ja"))
    }

    @Test
    fun everyLanguageRoundTripsThroughItsTag() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromTag(language.tag))
        }
    }

    @Test
    fun systemDefaultIsListedFirstThenNineLanguages() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.entries.first())
        assertEquals(9, AppLanguage.entries.count { it.tag != null })
    }
}
