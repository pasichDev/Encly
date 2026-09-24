package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles

@Composable
fun TextBlock(
    block: Block.TextBlock,
    blockActions: BlockActions,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val text by block.text.collectAsState()
    val fontStyles = rememberFontStyles()
    var textFieldValue by rememberBlockTextFieldValue(text, blockActions)

    BasicTextField(
        value = textFieldValue,
        enabled = !isLocked,
        onValueChange = { newValue ->
            textFieldValue = newValue
            blockActions.onTextChanged(newValue.text)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontStyles.sizes.textBlock,
            fontFamily = fontStyles.families.body,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier =
        modifier
            .fillMaxWidth()
            .textBlockKeys(text, blockActions, enterAddsParagraph = true),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(block.placeholder),
                        style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = fontStyles.sizes.textBlock,
                            fontFamily = fontStyles.families.body,
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
