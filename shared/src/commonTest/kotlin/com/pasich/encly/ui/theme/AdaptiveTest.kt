package com.pasich.encly.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdaptiveTest {
    @Test
    fun aPhoneIsCompactInBothOrientations() {
        assertEquals(WidthClass.Compact, WidthClass.of(width = 411.dp, height = 891.dp))
        // Held sideways it is wide but low: still a phone.
        assertEquals(WidthClass.Compact, WidthClass.of(width = 891.dp, height = 411.dp))
    }

    @Test
    fun theClassGrowsWithTheWidthAtTheMaterialBreakpoints() {
        assertEquals(WidthClass.Compact, WidthClass.of(width = 599.dp, height = 800.dp))
        assertEquals(WidthClass.Medium, WidthClass.of(width = 600.dp, height = 800.dp))
        assertEquals(WidthClass.Medium, WidthClass.of(width = 839.dp, height = 800.dp))
        assertEquals(WidthClass.Expanded, WidthClass.of(width = 840.dp, height = 800.dp))
        assertEquals(WidthClass.Expanded, WidthClass.of(width = 1920.dp, height = 1080.dp))
    }

    @Test
    fun aNarrowDesktopWindowFallsBackToThePhoneLayout() {
        assertEquals(WidthClass.Compact, WidthClass.of(width = 480.dp, height = 900.dp))
    }

    @Test
    fun onlyWideClassesAreWide() {
        assertTrue(!WidthClass.Compact.isWide)
        assertTrue(WidthClass.Medium.isWide)
        assertTrue(WidthClass.Expanded.isWide)
    }

    @Test
    fun theDesktopDensityNeedsAnExpandedWindowAndAPointer() {
        assertEquals(DensityProfile.Desktop, DensityProfile.of(WidthClass.Expanded, pointerFirst = true))
        assertEquals(DensityProfile.Touch, DensityProfile.of(WidthClass.Expanded, pointerFirst = false))
        assertEquals(DensityProfile.Touch, DensityProfile.of(WidthClass.Medium, pointerFirst = true))
        assertEquals(DensityProfile.Touch, DensityProfile.of(WidthClass.Compact, pointerFirst = true))
    }

    @Test
    fun theTouchSpacingIsExactlyTheOneThePhoneAlwaysHad() {
        assertEquals(EnclySpacing(), enclySpacingFor(DensityProfile.Touch))
        assertEquals(56.dp, enclySpacingFor(DensityProfile.Touch).rowHeight)
        assertEquals(64.dp, enclySpacingFor(DensityProfile.Touch).topBarHeight)
    }

    @Test
    fun theDesktopSpacingIsTighterThanTheTouchOne() {
        val touch = enclySpacingFor(DensityProfile.Touch)
        val desktop = enclySpacingFor(DensityProfile.Desktop)

        assertNotEquals(touch, desktop)
        assertTrue(desktop.rowHeight < touch.rowHeight)
        assertTrue(desktop.topBarHeight < touch.topBarHeight)
        assertTrue(desktop.buttonHeight < touch.buttonHeight)
        assertTrue(desktop.listGutter < touch.listGutter)
        // The scale itself (4, 8, 12, 16, 24, 32, 48) does not change, and neither does the icon size.
        assertEquals(touch.m, desktop.m)
        assertEquals(touch.icon, desktop.icon)
    }
}
