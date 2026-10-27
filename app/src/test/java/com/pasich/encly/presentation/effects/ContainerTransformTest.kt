package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.screens.ComposeScreenTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card-to-editor modifier must never keep a leaving screen composed: a re-lock cannot wait for a fade. */
class ContainerTransformTest : ComposeScreenTest() {
    private companion object {
        const val CHECK_AFTER_MS = 80L
    }

    private var shown by mutableStateOf(true)

    @Test
    fun controlWithoutTheModifier() = leaves(shared = false)

    @Test
    fun aScreenLeavingWithNoExitIsGoneAtOnceWhateverItShares() = leaves(shared = true)

    private fun leaves(shared: Boolean) {
        setScreen {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    AnimatedVisibility(visible = shown, exit = ExitTransition.None) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val container = if (shared) {
                                rememberNoteContainerModifier(5L, ContainerEnd.EDITOR)
                            } else {
                                Modifier
                            }
                            Box(
                                Modifier
                                    .testTag("editor")
                                    .size(40.dp)
                                    .then(container),
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("editor").assertExists()

        val before = rule.mainClock.currentTime
        rule.runOnUiThread { shown = false }
        rule.waitForIdle()
        val elapsed = rule.mainClock.currentTime - before

        rule.onNodeWithTag("editor").assertDoesNotExist()
        // A shared fade would hold the leaving screen for its 300 ms.
        assertTrue("left after $elapsed ms", elapsed < CHECK_AFTER_MS)
    }
}
