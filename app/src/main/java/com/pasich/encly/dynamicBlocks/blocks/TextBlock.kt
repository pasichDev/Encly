package com.pasich.encly.dynamicBlocks.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockActions
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.dynamicBlocks.focus.KeyboardUtils
import com.pasich.encly.ui.theme.bodyNote
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles

@Composable
fun TextBlock(
    block: Block.TextBlock,
    blockActions: BlockActions,
    modifier: Modifier,
    isLocked: Boolean = false,
    index: Int,
) {
    val text by block.text.collectAsState()
    val fontStyles = rememberFontStyles()
    val oldText = remember { mutableStateOf(text) }

    // To support placing the cursor at the end of the text
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text,
            ),
        )
    }

    // Synchronize textFieldValue with block.text
    LaunchedEffect(text) {
        if (textFieldValue.text != text) {
            textFieldValue =
                TextFieldValue(
                    text = text,
                    selection =
                        TextRange(text.length),
                )
        }
    }

    // Register a callback for placing the cursor at the end of the text
    LaunchedEffect(index) {
        if (blockActions is com.pasich.encly.dynamicBlocks.BlockActionsImpl) {
            blockActions.viewModel.focusManager.registerCursorToEndCallback(index) {
                textFieldValue =
                    textFieldValue.copy(
                        selection =
                            TextRange(textFieldValue.text.length),
                    )
            }
        }
    }

    BasicTextField(
        value = textFieldValue,
        enabled = !isLocked,
        onValueChange = { newValue ->
            textFieldValue = newValue
            val newText = newValue.text
            if (text != newText) {
                blockActions.onTextChanged(oldText.value, newText)
                oldText.value = newText
            }
            block.text.value = newText
        },
        textStyle = bodyNote.copy(
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = fontStyles.sizes.textBlock,
            fontFamily = fontStyles.families.body
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        blockActions.updateLastInteractionIndex(index)
                    }
                }.onKeyEvent { event ->
                    KeyboardUtils.handleKeyEvent(
                        event = event,
                        text = text,
                        onBackspaceEmpty = {
                            blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE)
                        },
                        onNavigateUp = { blockActions.navigateToPrevious() },
                        onNavigateDown = {
                            val navigated = blockActions.navigateToNext()
                            if (!navigated) {
                                blockActions.onAddParagraph()
                            }
                            true
                        },
                        onEnterPressed = {
                            blockActions.onAddParagraph()
                            true
                        },
                    )
                },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(block.placeholder),
                        style =
                            bodyNote.copy(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                fontSize = fontStyles.sizes.textBlock,
                                fontFamily = fontStyles.families.body
                            ),
                    )
                }
                innerTextField()
            }
        },
    )
}
