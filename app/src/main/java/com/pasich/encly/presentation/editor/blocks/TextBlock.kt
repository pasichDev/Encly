package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.BlockActions
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
    val state = rememberBlockTextFieldState(block.text, blockActions)
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontStyles.sizes.textBlock,
        fontFamily = fontStyles.families.body,
    )
    val onEnter = remember(blockActions) { blockActions.addParagraphOnEnter() }

    BasicTextField(
        state = state,
        // Read-only, not disabled: a locked note's text can still be selected and copied.
        readOnly = isLocked,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = WritingKeyboard,
        modifier = modifier
            .fillMaxWidth()
            .textBlockKeys(state, blockActions, onEnter),
        decorator = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (showPlaceholder && state.text.isEmpty()) {
                    Text(
                        text = stringResource(block.placeholder),
                        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                innerTextField()
            }
        },
    )
}
