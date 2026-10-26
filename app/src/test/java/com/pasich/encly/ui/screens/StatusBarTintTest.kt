package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.pasich.encly.presentation.designsystem.LocalStatusBarTint
import com.pasich.encly.presentation.designsystem.StatusBarTint
import com.pasich.encly.presentation.designsystem.TintStatusBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusBarTintTest : ComposeScreenTest() {
    private val tint = StatusBarTint()

    @Test
    fun aScreenTintsTheStatusBarWhileItIsShownAndClearsItWhenItLeaves() {
        var shown by mutableStateOf(true)
        setScreen {
            CompositionLocalProvider(LocalStatusBarTint provides tint) {
                if (shown) TintStatusBar(Color.Red)
            }
        }
        rule.waitForIdle()
        assertEquals(Color.Red, tint.color)

        shown = false
        rule.waitForIdle()

        assertNull(tint.color)
    }

    @Test
    fun theTintFollowsAChangedPageColour() {
        var page by mutableStateOf(Color.Red)
        setScreen { CompositionLocalProvider(LocalStatusBarTint provides tint) { TintStatusBar(page) } }
        rule.waitForIdle()

        page = Color.Blue
        rule.waitForIdle()

        assertEquals(Color.Blue, tint.color)
    }
}
