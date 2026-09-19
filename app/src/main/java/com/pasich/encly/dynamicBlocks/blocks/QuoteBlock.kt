package com.pasich.encly.dynamicBlocks.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockActions
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.dynamicBlocks.focus.KeyboardUtils
import com.pasich.encly.ui.theme.bodyNote
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles


@Composable
fun QuoteBlock(
    block: Block.QuoteBlock,
    blockActions: BlockActions,
    modifier: Modifier,
    isLocked: Boolean = false,
    index: Int
) {
    val text by block.text.collectAsState()
    val fontStyles = rememberFontStyles()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        // To track the previous text value
        val oldText = remember { mutableStateOf(text) }

        // To support placing the cursor at the end of the text
        var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text)) }

        // Synchronize textFieldValue with block.text
        LaunchedEffect(text) {
            if (textFieldValue.text != text) {
                textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                    text = text,
                    selection = androidx.compose.ui.text.TextRange(text.length)
                )
            }
        }
        
        // Register a callback for placing the cursor at the end of the text
        LaunchedEffect(index) {
            if (blockActions is com.pasich.encly.dynamicBlocks.BlockActionsImpl) {
                blockActions.viewModel.focusManager.registerCursorToEndCallback(index) {
                    textFieldValue = textFieldValue.copy(
                        selection = androidx.compose.ui.text.TextRange(textFieldValue.text.length)
                    )
                }
            }
        }
        
        BasicTextField(
            value = textFieldValue,
            enabled = !isLocked,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.PasswordVisible),
            onValueChange = { newValue ->
                textFieldValue = newValue
                val newText = newValue.text
                // Register the text change for undo/redo
                if (text != newText) {
                    blockActions.onTextChanged(oldText.value, newText)
                    oldText.value = newText
                }
                block.text.value = newText
            },
            textStyle = bodyNote.copy(
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                fontSize = fontStyles.sizes.quote,
                fontFamily = fontStyles.families.body
            ),
            modifier = modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        blockActions.updateLastInteractionIndex(index)
                    }
                }
                .onKeyEvent { event ->
                    KeyboardUtils.handleKeyEvent(
                        event = event,
                        text = text,
                        onBackspaceEmpty = {
                            blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE)
                        },
                        onNavigateUp = { blockActions.navigateToPrevious() },
                        onNavigateDown = { 
                            // Try to navigate to the next block
                            val navigated = blockActions.navigateToNext()
                            // If navigation is not possible (this is the last block), add a new paragraph
                            if (!navigated) {
                                blockActions.onAddParagraph()
                            }
                            true // Always return true to prevent adding an Enter
                        }
                    )
                }
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(bottomEnd = 10.dp, topEnd = 10.dp)
                )
                .padding(8.dp),
            decorationBox = {
                Box(
                    modifier = modifier.fillMaxWidth()
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quote), style = bodyNote.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6F),
                                fontStyle = FontStyle.Italic,
                                fontSize = fontStyles.sizes.quote,
                                fontFamily = fontStyles.families.body
                            )
                        )
                    }
                    it()
                }
            })
    }
}

