package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.StateFlow

private const val LEFT_TO_RIGHT_MARK = '\u200E'
private const val RIGHT_TO_LEFT_MARK = '\u200F'
private const val ARABIC_LETTER_MARK = '\u061C'

/** The scripts written right to left: Hebrew, Arabic, Syriac, Thaana, NKo, Samaritan, Mandaic, Arabic Extended. */
private val RtlBlocks = '\u0590'..'\u08FF'

/** Hebrew and Arabic presentation forms. */
private val RtlPresentationForms = '\uFB1D'..'\uFDFF'
private val RtlPresentationFormsB = '\uFE70'..'\uFEFF'

/**
 * The direction [text] reads in, by its first strong character (Android's `FIRSTSTRONG` rule, which is also
 * what Compose picks for a paragraph): a letter of a right-to-left script, or a right-to-left mark, makes it
 * right to left; any other letter, or a left-to-right mark, left to right. Digits, punctuation, symbols and
 * emoji are neutral and skipped. Null when there is no strong character at all (an empty or neutral text).
 */
internal fun firstStrongDirection(text: CharSequence): LayoutDirection? =
    text.asSequence().mapNotNull(::strongDirectionOf).firstOrNull()

/** The direction [char] is strong in, or null for a neutral or weak one. */
private fun strongDirectionOf(char: Char): LayoutDirection? = when {
    char == LEFT_TO_RIGHT_MARK -> LayoutDirection.Ltr
    char == RIGHT_TO_LEFT_MARK || char == ARABIC_LETTER_MARK -> LayoutDirection.Rtl
    !char.isLetter() -> null
    char.isRightToLeftScript() -> LayoutDirection.Rtl
    else -> LayoutDirection.Ltr
}

private fun Char.isRightToLeftScript(): Boolean =
    this in RtlBlocks || this in RtlPresentationForms || this in RtlPresentationFormsB

/**
 * Lays [content] out in the direction the paragraph [text] reads in: a block of Arabic text gets its
 * marker, checkbox, indent and quote marks on the right, a block of Latin text on the left, whatever
 * direction the app itself has. A text with no strong character follows the app's direction.
 *
 * [text] is read in a derived state, so only a change of direction recomposes [content], not each key.
 */
@Composable
internal fun ReadingDirection(text: () -> CharSequence, content: @Composable () -> Unit) {
    val appDirection = LocalLayoutDirection.current
    val latest by rememberUpdatedState(text)
    val direction by remember(appDirection) {
        derivedStateOf { firstStrongDirection(latest()) ?: appDirection }
    }
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

/** [ReadingDirection] for a block whose live text is [text]. */
@Composable
internal fun ReadingDirection(text: StateFlow<String>, content: @Composable () -> Unit) {
    val current: State<String> = text.collectAsState()
    ReadingDirection(text = { current.value }, content = content)
}
