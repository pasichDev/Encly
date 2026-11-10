package com.pasich.encly.editor.ui.blocks

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SeparatorBlockTest {
    @get:Rule
    val rule = createComposeRule()

    private var opened = 0

    private fun show() = rule.setContent {
        MaterialTheme { SeparatorBlock(onClick = { opened++ }) }
    }

    @Test
    fun theTapTargetIsAboutTheRuleNotAFullFingerHighBand() {
        show()

        rule.onNode(hasClickAction()).assertHeightIsEqualTo(24.dp)
    }

    @Test
    fun tappingItStillOpensTheMenu() {
        show()

        rule.onNode(hasClickAction()).performClick()

        assertEquals(1, opened)
    }
}
