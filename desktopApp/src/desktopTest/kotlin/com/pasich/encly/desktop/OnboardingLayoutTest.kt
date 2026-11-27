package com.pasich.encly.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.pasich.encly.core.keystore.DesktopPasswordPolicy
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.onboarding_get_started
import com.pasich.encly.core.resources.onboarding_passphrase_again_title
import com.pasich.encly.core.resources.onboarding_phrase_done
import com.pasich.encly.core.resources.onboarding_phrase_title
import com.pasich.encly.core.resources.onboarding_recovery_create
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.resources.passphrase_suggest
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.platform.AppWindow
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertTrue

/** The first-run card of the desktop app in the smallest window it is meant for: nothing is cut off. */
@OptIn(ExperimentalTestApi::class)
class OnboardingLayoutTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun text(res: StringResource): String = runBlocking { getString(res) }

    private fun DesktopComposeUiTest.waitForText(res: StringResource) {
        val shown = text(res)
        waitUntil(timeoutMillis = LONG_WAIT_MS) { onAllNodesWithText(shown).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun DesktopComposeUiTest.press(res: StringResource) {
        waitForText(res)
        onNode(hasText(text(res)) and hasClickAction()).performClick()
        waitForIdle()
    }

    private fun DesktopComposeUiTest.field() = run {
        val label = text(Res.string.passphrase_label)
        waitUntil(timeoutMillis = LONG_WAIT_MS) {
            onAllNodesWithContentDescription(label).fetchSemanticsNodes().size ==
                1
        }
        onAllNodesWithContentDescription(label)[0]
    }

    private fun DesktopComposeUiTest.typedPassphrase(): String =
        field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun theTwelveWordsScrollInTheCardAndTheButtonStaysInReach() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val graph = createGraphFactory<DesktopAppGraph.Factory>()
            .create(testEnvironment(temp.root.toPath(), secretPolicy = DesktopPasswordPolicy()))
        setContent { DesktopApp(graph, AppWindow.None) }

        press(Res.string.onboarding_get_started)
        press(Res.string.passphrase_suggest)
        // The suggestion is in the field: continue, then confirm it with the same text (typed back).
        val suggested = typedPassphrase()
        press(Res.string.action_continue)
        waitForText(Res.string.onboarding_passphrase_again_title)
        field().performTextInput(suggested)
        press(Res.string.action_continue)
        press(Res.string.onboarding_recovery_create)
        waitForText(Res.string.onboarding_phrase_title)
        waitForIdle()

        val done = onNode(hasText(text(Res.string.onboarding_phrase_done)) and hasClickAction())
        done.assertIsDisplayed()
        val bounds = done.getBoundsInRoot()
        assertTrue(bounds.bottom <= HEIGHT.dp && bounds.top >= 0.dp, "the button is out of the window: $bounds")

        // The last row of words is below the fold of the card's scrolling part; scrolling brings it into view.
        val last = onNode(hasText("12"))
        last.performScrollTo()
        last.assertIsDisplayed()
        done.assertIsDisplayed()
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
        const val LONG_WAIT_MS = 60_000L
    }
}
