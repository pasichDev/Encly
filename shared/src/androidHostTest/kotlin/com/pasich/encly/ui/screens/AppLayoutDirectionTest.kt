package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.presentation.navigation.appLayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

/** The layout direction follows the app's language on every host, not only where the platform derives it. */
class AppLayoutDirectionTest : ComposeScreenTest() {
    private fun directionWhenHostIs(host: LayoutDirection): LayoutDirection {
        var direction: LayoutDirection? = null
        setScreen {
            CompositionLocalProvider(LocalLayoutDirection provides host) { direction = appLayoutDirection() }
        }
        return direction!!
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    fun arabicIsLaidOutRightToLeftEvenWhereTheHostSaysOtherwise() {
        assertEquals(LayoutDirection.Rtl, directionWhenHostIs(LayoutDirection.Ltr))
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun englishIsLaidOutLeftToRightEvenWhereTheHostSaysOtherwise() {
        assertEquals(LayoutDirection.Ltr, directionWhenHostIs(LayoutDirection.Rtl))
    }

    @Test
    @Config(qualifiers = "ja-w411dp-h891dp-xxhdpi")
    fun aLanguageTheAppDoesNotHaveKeepsTheHostsRightToLeftDirection() {
        assertEquals(LayoutDirection.Rtl, directionWhenHostIs(LayoutDirection.Rtl))
    }

    @Test
    @Config(qualifiers = "ja-w411dp-h891dp-xxhdpi")
    fun aLanguageTheAppDoesNotHaveKeepsTheHostsLeftToRightDirection() {
        assertEquals(LayoutDirection.Ltr, directionWhenHostIs(LayoutDirection.Ltr))
    }
}
