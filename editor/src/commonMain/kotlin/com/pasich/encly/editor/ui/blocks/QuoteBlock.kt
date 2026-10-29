package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.theme.EDITOR_PLACEHOLDER_ALPHA
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.theme.lineHeight
import com.pasich.encly.editor.ui.BlockActions

/** The closing quote mark is the opening one turned around. */
private const val HALF_TURN = 180f

/**
 * [focusRequester] and [fieldModifier] reach the quote's text field, [modifier] the card that
 * draws the quote.
 */
@Composable
internal fun QuoteBlock(
    block: Block.QuoteBlock,
    blockActions: BlockActions,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val theme = LocalEditorTheme.current
    val spacing = theme.spacing
    val state = rememberBlockTextFieldState(block.text, blockActions)
    val actions by rememberUpdatedState(blockActions)
    // Enter ends the quote: the text after the cursor goes on as a paragraph (BlockInput).
    val input = remember { BlockInput(BlockField.Styled) { actions } }
    val highlight = rememberFieldHighlight(blockActions.highlights, block.id)
    RegisterCaretAtX(blockActions, state, highlight)
    var focused by remember { mutableStateOf(false) }
    val quoteStyle = theme.quoteStyle.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = theme.fontSizes.quote,
        lineHeight = theme.lineSpacing.lineHeight(
            fontSize = theme.fontSizes.quote,
            normal = theme.quoteStyle.lineHeight,
        ),
    )

    // A tonal card between an opening and a closing quote mark in `primary`, 8 dp below the block
    // above (design spec §4.4).
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.labelGap),
        modifier = modifier
            .padding(top = spacing.xs)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = spacing.fieldGap, vertical = spacing.m),
    ) {
        QuoteMark()
        BasicTextField(
            state = state,
            readOnly = isLocked,
            inputTransformation = input,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = WritingKeyboard,
            textStyle = quoteStyle,
            onTextLayout = highlight.onTextLayout,
            modifier = fieldModifier
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .fillMaxWidth()
                .then(highlight.modifier)
                .textBlockKeys(state, blockActions, highlight),
            decorator = { innerTextField ->
                QuoteDecoration(state.text.isEmpty() && focused, quoteStyle, innerTextField)
            },
        )
        // The closing mark: the opening one turned around, at the end of the quote.
        QuoteMark(Modifier.align(Alignment.End).rotate(HALF_TURN))
    }
}

/** A quote mark in `primary`; the closing one is this turned around. */
@Composable
private fun QuoteMark(modifier: Modifier = Modifier) {
    val theme = LocalEditorTheme.current
    Icon(
        theme.icons.quote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(theme.spacing.iconMedium),
    )
}

@Composable
private fun QuoteDecoration(showPlaceholder: Boolean, style: TextStyle, innerTextField: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (showPlaceholder) {
            Text(
                text = LocalEditorStrings.current.text(EditorString.QUOTE),
                style = style.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EDITOR_PLACEHOLDER_ALPHA),
                ),
            )
        }
        innerTextField()
    }
}
