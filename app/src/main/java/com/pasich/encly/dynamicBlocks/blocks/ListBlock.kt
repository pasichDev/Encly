package com.pasich.encly.dynamicBlocks.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockActions
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.focus.KeyboardUtils
import com.pasich.encly.ui.theme.bodyNote
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles

@Composable
fun ListBlock(
    block: Block.ListBlock,
    blockActions: BlockActions,
    isLocked: Boolean = false,
    index: Int,
) {
    val itemsList by block.items.collectAsState()
    val fontStyles = rememberFontStyles()
    var focusedItemIndex by remember { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Відслідковуємо чи це перша ініціалізація списку
    var isInitialized by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        itemsList.forEachIndexed { itemIndex, item ->
            Row(verticalAlignment = if (block.blockType == BlockType.LIST_NUMBER) Alignment.Top else Alignment.CenterVertically) {
                when (block.blockType) {
                    BlockType.LIST_NUMBER ->
                        Text(
                            "${itemIndex + 1}.",
                            style = bodyNote.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontStyles.families.body,
                                fontSize = fontStyles.sizes.list
                            ),
                        )

                    BlockType.LIST_CHECK ->
                        Checkbox(
                            checked = item.isCheck,
                            enabled = !isLocked,
                            onCheckedChange = {
                                block.items.value =
                                    block.items.value.toMutableList().apply {
                                        this[itemIndex] = item.copy(isCheck = it)
                                    }
                            },
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .scale(0.7f),
                        )

                    else -> Unit
                }

                Spacer(modifier = Modifier.width(15.dp))
                BasicTextField(
                    value = item.value,
                    enabled = !item.isCheck && !isLocked,
                    textStyle =
                        bodyNote.copy(
                            color = if (item.isCheck) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (item.isCheck) TextDecoration.LineThrough else TextDecoration.None,
                            fontFamily = fontStyles.families.body,
                            fontSize = fontStyles.sizes.list
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    onValueChange = { updatedValue ->
                        block.items.value =
                            block.items.value.toMutableList().apply {
                                this[itemIndex] = item.copy(value = updatedValue)
                            }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(if (focusedItemIndex == itemIndex) focusRequester else FocusRequester.Default)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    blockActions.updateLastInteractionIndex(index)
                                    focusedItemIndex =
                                        itemIndex // Встановлюємо фокус тільки при фактичній взаємодії
                                }
                            }
                            .onKeyEvent { event ->
                                KeyboardUtils.handleKeyEvent(
                                    event = event,
                                    text = item.value,
                                    onBackspaceEmpty = {
                                        if (itemsList.size == 1) {
                                            // Якщо це останній елемент, видаляємо весь блок
                                            blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE_LIST)
                                        } else {
                                            // Видаляємо поточний елемент
                                            block.items.value =
                                                block.items.value.toMutableList().apply {
                                                    removeAt(itemIndex)
                                                }
                                            // Встановлюємо фокус на попередній елемент якщо можливо
                                            if (itemIndex > 0) {
                                                focusedItemIndex = itemIndex - 1
                                            } else if (itemsList.size > 1) {
                                                focusedItemIndex = 0
                                            }
                                        }
                                    },
                                    onNavigateUp = { blockActions.navigateToPrevious() },
                                    onNavigateDown = {
                                        val navigated = blockActions.navigateToNext()
                                        if (!navigated) {
                                            // Додаємо новий елемент списку після поточного
                                            block.items.value =
                                                block.items.value.toMutableList().apply {
                                                    add(itemIndex + 1, ItemListBlock(""))
                                                }
                                            focusedItemIndex = itemIndex + 1
                                        }
                                        true
                                    },
                                    onEnterPressed = {
                                        if (item.value.isNotEmpty()) {
                                            // Додаємо новий елемент списку після поточного
                                            block.items.value =
                                                block.items.value.toMutableList().apply {
                                                    add(itemIndex + 1, ItemListBlock(""))
                                                }
                                            focusedItemIndex = itemIndex + 1
                                        } else {
                                            // На порожньому елементі - виходимо зі списку і створюємо параграф
                                            focusedItemIndex = null
                                            if (itemsList.size == 1) {
                                                blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE_LIST)
                                            } else {
                                                block.items.value =
                                                    block.items.value.toMutableList().apply {
                                                        removeAt(itemIndex)
                                                    }
                                            }
                                            blockActions.onAddParagraph()
                                        }
                                        true
                                    },
                                )
                            },
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (item.value.isNotEmpty()) {
                                    // Додаємо новий елемент списку після поточного
                                    block.items.value =
                                        block.items.value.toMutableList().apply {
                                            add(itemIndex + 1, ItemListBlock(""))
                                        }
                                    // Встановлюємо фокус на новий елемент
                                    focusedItemIndex = itemIndex + 1
                                } else {
                                    // Користувач хоче вийти зі списку на порожньому елементі
                                    focusedItemIndex = null // Очищаємо локальний фокус

                                    if (itemsList.size == 1) {
                                        // Якщо це єдиний елемент у списку, замінюємо весь список на текстовий блок
                                        blockActions.onReplaceBlock(Block.TextBlock())
                                    } else {
                                        // Видаляємо порожній елемент
                                        block.items.value =
                                            block.items.value.toMutableList().apply {
                                                removeAt(itemIndex)
                                            }
                                        // Додаємо новий текстовий параграф після всього списку
                                        blockActions.onAddParagraph()
                                    }
                                }
                            },
                        ),
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                        ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (item.value.isEmpty()) {
                                Text(
                                    text = "List item",
                                    style =
                                        bodyNote.copy(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            fontFamily = fontStyles.families.body,
                                            fontSize = fontStyles.sizes.list
                                        ),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
    }

    // Керування фокусом для нових елементів списку
    LaunchedEffect(focusedItemIndex) {
        focusedItemIndex?.let {
            focusRequester.requestFocus()
        }
    }

    // Автоматичний фокус на першому елементі при ініціалізації списку
    LaunchedEffect(itemsList, index) {
        if (!isInitialized && itemsList.isNotEmpty()) {
            // Перевіряємо чи це новий порожній список:
            // - Перший елемент порожній
            // - Список містить тільки один елемент
            val firstItem = itemsList.firstOrNull()
            if (firstItem != null &&
                firstItem.value.isEmpty() &&
                itemsList.size == 1 &&
                focusedItemIndex == null
            ) {
                // Додаємо невелику затримку для завершення рендерингу
                kotlinx.coroutines.delay(50)
                focusedItemIndex = 0
                isInitialized = true
            } else if (itemsList.isNotEmpty()) {
                // Якщо список вже має контент, позначаємо як ініціалізований
                isInitialized = true
            }
        }
    }

    // НЕ встановлюємо автоматичний фокус при створенні списку
    // Фокус буде встановлений тільки коли користувач явно взаємодіє зі списком
}
