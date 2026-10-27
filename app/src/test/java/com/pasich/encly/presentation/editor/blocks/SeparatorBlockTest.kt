package com.pasich.encly.presentation.editor.blocks

import android.app.Application
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.ui.theme.EnclyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SeparatorBlockTest {
    @get:Rule
    val rule = createComposeRule()

    private var opened = 0

    private fun show() = rule.setContent {
        EnclyTheme(settings = ThemeSettings(), dark = false) { SeparatorBlock(onClick = { opened++ }) }
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
