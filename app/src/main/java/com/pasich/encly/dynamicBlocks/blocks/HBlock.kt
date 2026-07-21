package com.pasich.encly.dynamicBlocks.blocks

import com.pasich.encly.core.AppLogger
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockActions
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.focus.KeyboardUtils
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles

@Composable
fun HBlock(
    block: Block.HBlock,
    blockActions: BlockActions,
    modifier: Modifier,
    isLocked: Boolean = false,
    index: Int,
) {
    val text by block.text.collectAsState()
    val fontStyles = rememberFontStyles()
    AppLogger.d("HBlock", "HBlock composed: index=$index, text='$text', blockType=${block.blockType}, isLocked=$isLocked")

    val textStyle: TextStyle =
        when (block.blockType) {
            BlockType.H1 -> MaterialTheme.typography.headlineSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = fontStyles.sizes.h1,
                fontFamily = fontStyles.families.heading
            )
            BlockType.H2 -> MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = fontStyles.sizes.h2,
                fontFamily = fontStyles.families.heading
            )
            BlockType.H3 -> MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = fontStyles.sizes.h3,
                fontFamily = fontStyles.families.heading
            )
            BlockType.H4 -> MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = fontStyles.sizes.h4,
                fontFamily = fontStyles.families.heading
            )
            else -> MaterialTheme.typography.titleMedium
        }

    val oldText = remember { mutableStateOf(text) }

    // Для підтримки встановлення курсора в кінець тексту
    var textFieldValue by remember {
        mutableStateOf(
            androidx.compose.ui.text.input
                .TextFieldValue(text),
        )
    }

    // Синхронізуємо textFieldValue з block.text
    LaunchedEffect(text) {
        if (textFieldValue.text != text) {
            textFieldValue =
                androidx.compose.ui.text.input.TextFieldValue(
                    text = text,
                    selection =
                        androidx.compose.ui.text
                            .TextRange(text.length),
                )
        }
    }

    // Реєструємо callback для встановлення курсора в кінець тексту
    LaunchedEffect(index) {
        if (blockActions is com.pasich.encly.dynamicBlocks.BlockActionsImpl) {
            blockActions.viewModel.focusManager.registerCursorToEndCallback(index) {
                textFieldValue =
                    textFieldValue.copy(
                        selection =
                            androidx.compose.ui.text
                                .TextRange(textFieldValue.text.length),
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
            AppLogger.d("HBlock", "onValueChange: oldText='$text', newText='$newText'")
            if (text != newText) {
                blockActions.onTextChanged(oldText.value, newText)
                oldText.value = newText
            }
            block.text.value = newText
        },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions =
            KeyboardActions(onNext = {
                AppLogger.d("HBlock", "onNext called, text='$text', isEmpty=${text.isEmpty()}")

                if (text.isEmpty()) {
                    // Якщо HBlock порожній, замінюємо його на звичайний текстовий блок
                    AppLogger.d("HBlock", "Empty HBlock - replacing with TextBlock")
                    val newTextBlock = Block.TextBlock()
                    blockActions.onReplaceBlock(newTextBlock)
                } else {
                    // Якщо HBlock не порожній, спробувати навігувати до наступного блока
                    val navigated = blockActions.navigateToNext()
                    // Якщо навігація неможлива (це останній блок), додати новий параграф
                    if (!navigated) {
                        blockActions.onAddParagraph()
                    }
                }
            }),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .onFocusChanged { focusState ->
                    AppLogger.d("HBlock", "Focus changed: index=$index, isFocused=${focusState.isFocused}")
                    if (focusState.isFocused) {
                        blockActions.updateLastInteractionIndex(index)
                    }
                }.onKeyEvent { event ->
                    AppLogger.d("HBlock", "onKeyEvent called: key=${event.key}, text='$text'")
                    KeyboardUtils.handleKeyEvent(
                        event = event,
                        text = text,
                        onBackspaceEmpty = {
                            blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE)
                        },
                        onNavigateUp = {
                            blockActions.navigateToPrevious()
                        },
                        onNavigateDown = {
                            AppLogger.d("HBlock", "onNavigateDown called")
                            // Спробувати навігувати до наступного блока
                            val navigated = blockActions.navigateToNext()
                            // Якщо навігація неможлива (це останній блок), додати новий параграф
                            if (!navigated) {
                                blockActions.onAddParagraph()
                            }
                            true // Завжди повертаємо true, щоб запобігти додаванню Enter
                        },
                        onEnterPressed = {
                            // Enter обробляється в KeyboardActions.onNext, тому тут не потрібен
                            AppLogger.d("HBlock", "onEnterPressed in onKeyEvent (should not be called for Enter)")
                            false
                        },
                    )
                },
        decorationBox = {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = block.blockType.toString(),
                        style = textStyle.copy(color = MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                it()
            }
        },
    )
}
