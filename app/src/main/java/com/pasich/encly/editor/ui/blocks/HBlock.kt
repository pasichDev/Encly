package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.theme.EDITOR_PLACEHOLDER_ALPHA
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.BlockActions

@Composable
fun HBlock(block: Block.HBlock, blockActions: BlockActions, modifier: Modifier = Modifier, isLocked: Boolean = false) {
    val textStyle = headingTextStyle(block.blockType)
    val state = rememberBlockTextFieldState(block.text, blockActions)
    val actions by rememberUpdatedState(blockActions)
    // Enter splits the heading; the text after the cursor goes on as a paragraph (BlockInput).
    val input = remember { BlockInput(BlockField.Styled) { actions } }
    val highlight = rememberFieldHighlight(blockActions.highlights, block.id)
    RegisterCaretAtX(blockActions, state, highlight)
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        state = state,
        readOnly = isLocked,
        inputTransformation = input,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = textStyle,
        keyboardOptions = WritingKeyboard,
        onTextLayout = highlight.onTextLayout,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .fillMaxWidth()
            .padding(top = LocalEditorTheme.current.spacing.labelGap)
            .then(highlight.modifier)
            .textBlockKeys(state, blockActions, highlight),
        decorator = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.text.isEmpty() && focused) {
                    Text(
                        text = LocalEditorStrings.current.heading(block.blockType.headingLevel()),
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EDITOR_PLACEHOLDER_ALPHA),
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}

private val HeadingTypes = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4)

/** 1 for H1 up to 4 for H4. */
private fun BlockType.headingLevel(): Int = HeadingTypes.indexOf(this) + 1

/** The text style of a heading of [blockType] (H1-H4). */
@Composable
private fun headingTextStyle(blockType: BlockType): TextStyle {
    val theme = LocalEditorTheme.current
    val (base, size) = when (blockType) {
        BlockType.H1 -> MaterialTheme.typography.headlineSmall to theme.fontSizes.h1
        BlockType.H2 -> MaterialTheme.typography.titleLarge to theme.fontSizes.h2
        BlockType.H3 -> MaterialTheme.typography.titleMedium to theme.fontSizes.h3
        BlockType.H4 -> MaterialTheme.typography.titleSmall to theme.fontSizes.h4
        else -> return MaterialTheme.typography.titleMedium
    }
    return base.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = size,
        fontFamily = theme.fontFamilies.heading,
    )
}
