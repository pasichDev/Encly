package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.EDITOR_PLACEHOLDER_ALPHA
import com.pasich.encly.presentation.designsystem.lineHeight
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.LocalNoteLineSpacing
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles

/**
 * Keyboard of the note's writing fields: sentence case, suggestions and autocorrect. The app
 * already asks keyboards not to learn from what is typed (SecureTextInputBoundary).
 */
internal val WritingKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    autoCorrectEnabled = true,
    keyboardType = KeyboardType.Text,
)

/** A paragraph. Its placeholder shows only when [showPlaceholder] (focused, or the note's only block). */
@Composable
fun TextBlock(
    block: Block.TextBlock,
    blockActions: BlockActions,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    showPlaceholder: Boolean = true,
) {
    val fontStyles = rememberFontStyles()
    val spacing = LocalNoteLineSpacing.current
    val state = rememberBlockTextFieldState(block.text, blockActions)
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontStyles.sizes.textBlock,
        fontFamily = fontStyles.families.body,
        lineHeight = spacing.lineHeight(fontStyles.sizes.textBlock, MaterialTheme.typography.bodyLarge.lineHeight),
    )
    val actions by rememberUpdatedState(blockActions)
    val input = remember { BlockInput(BlockField.Paragraph) { actions } }
    val highlight = rememberFieldHighlight(blockActions.highlights, block.id)

    BasicTextField(
        state = state,
        // Read-only, not disabled: a locked note's text can still be selected and copied.
        readOnly = isLocked,
        inputTransformation = input,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = WritingKeyboard,
        onTextLayout = highlight.onTextLayout,
        modifier = modifier
            .fillMaxWidth()
            .then(highlight.modifier)
            .textBlockKeys(state, blockActions),
        decorator = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                SlashMenuPopup(field = state, slash = blockActions.slash)
                if (showPlaceholder && state.text.isEmpty()) {
                    Text(
                        text = stringResource(block.placeholder),
                        style = style.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EDITOR_PLACEHOLDER_ALPHA),
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
