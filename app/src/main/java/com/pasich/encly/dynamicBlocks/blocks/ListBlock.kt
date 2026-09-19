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
import androidx.compose.ui.text.input.KeyboardType
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

    // Track whether this is the first initialization of the list
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
                                        itemIndex // Set focus only on actual interaction
                                }
                            }
                            .onKeyEvent { event ->
                                KeyboardUtils.handleKeyEvent(
                                    event = event,
                                    text = item.value,
                                    onBackspaceEmpty = {
                                        if (itemsList.size == 1) {
                                            // If this is the last item, remove the entire block
                                            blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE_LIST)
                                        } else {
                                            // Remove the current item
                                            block.items.value =
                                                block.items.value.toMutableList().apply {
                                                    removeAt(itemIndex)
                                                }
                                            // Set focus on the previous item if possible
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
                                            // Add a new list item after the current one
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
                                            // Add a new list item after the current one
                                            block.items.value =
                                                block.items.value.toMutableList().apply {
                                                    add(itemIndex + 1, ItemListBlock(""))
                                                }
                                            focusedItemIndex = itemIndex + 1
                                        } else {
                                            // On an empty item - exit the list and create a paragraph
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
                                    // Add a new list item after the current one
                                    block.items.value =
                                        block.items.value.toMutableList().apply {
                                            add(itemIndex + 1, ItemListBlock(""))
                                        }
                                    // Set focus on the new item
                                    focusedItemIndex = itemIndex + 1
                                } else {
                                    // The user wants to exit the list on an empty item
                                    focusedItemIndex = null // Clear local focus

                                    if (itemsList.size == 1) {
                                        // If this is the only item in the list, replace the entire list with a text block
                                        blockActions.onReplaceBlock(Block.TextBlock())
                                    } else {
                                        // Remove the empty item
                                        block.items.value =
                                            block.items.value.toMutableList().apply {
                                                removeAt(itemIndex)
                                            }
                                        // Add a new text paragraph after the entire list
                                        blockActions.onAddParagraph()
                                    }
                                }
                            },
                        ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
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

    // Focus management for new list items
    LaunchedEffect(focusedItemIndex) {
        focusedItemIndex?.let {
            focusRequester.requestFocus()
        }
    }

    // Automatic focus on the first item when the list is initialized
    LaunchedEffect(itemsList, index) {
        if (!isInitialized && itemsList.isNotEmpty()) {
            // Check whether this is a new empty list:
            // - The first item is empty
            // - The list contains only one item
            val firstItem = itemsList.firstOrNull()
            if (firstItem != null &&
                firstItem.value.isEmpty() &&
                itemsList.size == 1 &&
                focusedItemIndex == null
            ) {
                // Add a small delay to allow rendering to complete
                kotlinx.coroutines.delay(50)
                focusedItemIndex = 0
                isInitialized = true
            } else if (itemsList.isNotEmpty()) {
                // If the list already has content, mark it as initialized
                isInitialized = true
            }
        }
    }

    // Do NOT set automatic focus when the list is created
    // Focus will be set only when the user explicitly interacts with the list
}
