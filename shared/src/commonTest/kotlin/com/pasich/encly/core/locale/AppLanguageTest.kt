package com.pasich.encly.core.locale

import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar-EG"))
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
    fun systemDefaultIsListedFirstThenTenLanguages() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.entries.first())
        assertEquals(10, AppLanguage.entries.count { it.tag != null })
    }

    @Test
    fun onlyArabicIsLaidOutRightToLeft() {
        assertEquals(listOf(AppLanguage.ARABIC), AppLanguage.entries.filter { it.rightToLeft })
    }
}
