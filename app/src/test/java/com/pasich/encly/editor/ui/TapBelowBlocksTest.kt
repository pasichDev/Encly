package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TapBelowBlocksTest {
    @get:Rule
    val rule = createComposeRule()

    private var taps = 0
    private var itemClicks = 0

    private fun show(enabled: Boolean = true) {
        rule.setContent {
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize().testTag("page")) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().tapBelowBlocks(state, enabled) { taps++ }) {
                    item { Box(Modifier.fillMaxWidth().height(100.dp).testTag("plain")) }
                    item { Box(Modifier.fillMaxWidth().height(100.dp).clickable { itemClicks++ }.testTag("button")) }
                }
            }
        }
    }

    private fun tapAtDp(y: Float) {
        val density = rule.density.density
        rule.onNodeWithTag("page").performTouchInput { click(Offset(width / 2f, y * density)) }
    }

    @Test
    fun aTapFarBelowTheLastItemIsATapOnTheEmptyPage() {
        show()

        tapAtDp(600f)

        assertEquals(1, taps)
    }

    @Test
    fun aTapOnAnItemOrOnWhatItHandlesIsNot() {
        show()

        tapAtDp(50f) // a plain item
        tapAtDp(150f) // a clickable item: it takes the tap
        tapAtDp(199f) // its lower edge

        assertEquals(0, taps)
        assertEquals(2, itemClicks)
    }

    @Test
    fun aDisabledPageIgnoresTheTap() {
        show(enabled = false)

        tapAtDp(600f)

        assertEquals(0, taps)
    }
}
