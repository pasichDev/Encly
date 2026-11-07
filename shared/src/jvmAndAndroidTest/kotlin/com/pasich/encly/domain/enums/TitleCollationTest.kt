package com.pasich.encly.domain.enums

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Titles follow the alphabet of the current language, not the code-point order. */
class TitleCollationTest {
    private val previous = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(previous)

    private fun sorted(tag: String, vararg titles: String): List<String> {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return titles.sortedWith(titleCollator())
    }

    @Test
    fun caseDoesNotDecideTheOrder() {
        assertEquals(listOf("apple", "Banana", "cherry"), sorted("en", "cherry", "Banana", "apple"))
    }

    @Test
    fun germanPutsAUmlautNextToA() {
        assertEquals(listOf("Apfel", "Äpfel", "Zebra"), sorted("de", "Zebra", "Äpfel", "Apfel"))
    }

    @Test
    fun ukrainianFollowsItsAlphabetWithYiAfterI() {
        assertEquals(
            listOf("Дім", "Ера", "Ірина", "Їжа", "Яблуко"),
            sorted("uk", "Яблуко", "Їжа", "Ірина", "Ера", "Дім"),
        )
    }
}
