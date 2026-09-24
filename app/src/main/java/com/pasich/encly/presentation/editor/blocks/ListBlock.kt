package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.designsystem.CheckboxSize
import com.pasich.encly.presentation.designsystem.EnclyCheckbox
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.focus.KeyboardUtils
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme

/** From this many items numbers take two digits, and a wider hanging indent. */
private const val WIDE_MARKER_FROM = 10

private const val FOCUS_FRAMES = 5

/** Which item takes focus next, and whether its cursor goes to the end. */
private data class ItemFocus(val itemId: String, val atEnd: Boolean)

/** The list's item fields by item id, and the item to focus once it is composed. */
private class ListFocus {
    val requesters = mutableMapOf<String, FocusRequester>()
    val fieldStates = mutableMapOf<String, TextFieldState>()
    var pending by mutableStateOf<ItemFocus?>(null)
}

/**
 * The list's focus: the target the editor focuses the list through (its first item, or its last
 * with the cursor at the end), and focus moves between items once the item is composed.
 */
@Composable
private fun rememberListFocus(block: Block.ListBlock, blockActions: BlockActions): ListFocus {
    val focus = remember { ListFocus() }
    DisposableEffect(block, blockActions) {
        val unregister = blockActions.registerFieldsFocusTarget { atEnd ->
            val items = block.items.value
            (if (atEnd) items.lastOrNull() else items.firstOrNull())?.let { focus.pending = ItemFocus(it.id, atEnd) }
        }
        onDispose { unregister() }
    }
    // A new item is composed a frame or two after the edit that added it.
    LaunchedEffect(focus.pending) {
        val request = focus.pending ?: return@LaunchedEffect
        repeat(FOCUS_FRAMES) {
            val requester = focus.requesters[request.itemId]
            if (requester != null) {
                runCatching { requester.requestFocus() }
                if (request.atEnd) focus.fieldStates[request.itemId]?.edit { placeCursorAtEnd() }
                focus.pending = null
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
        focus.pending = null
    }
    return focus
}

/**
 * A checklist, bulleted or numbered list: one field per item. Every change goes through
 * [blockActions], so it is autosaved and can be undone. The editor focuses the list through the
 * target it registers ([BlockActions.registerFieldsFocusTarget]).
 */
@Composable
fun ListBlock(
    block: Block.ListBlock,
    blockActions: BlockActions,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val items by block.items.collectAsState()
    val actions by rememberUpdatedState(blockActions)
    val focus = rememberListFocus(block, blockActions)
    val editor = remember(block) { ListEditor(block, { actions }) { focus.pending = it } }
    val checklist = block.blockType == BlockType.LIST_CHECK
    val marker = ListMarker(
        type = block.blockType,
        // The hanging indent of bullets and numbers (spec §4.4); two-digit numbers need more.
        width = if (items.size >= WIDE_MARKER_FROM) EnclyTheme.spacing.xl else EnclyTheme.spacing.l,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (checklist) 0.dp else EnclyTheme.spacing.xxs),
    ) {
        items.forEachIndexed { itemIndex, item ->
            key(item.id) {
                val state = rememberSyncedTextFieldState(
                    model = { block.items.value.firstOrNull { it.id == item.id }?.value },
                    modelChanges = block.items,
                    onEdit = { text -> editor.setText(item.id, text) },
                )
                val requester = remember { FocusRequester() }
                DisposableEffect(item.id) {
                    focus.requesters[item.id] = requester
                    focus.fieldStates[item.id] = state
                    onDispose {
                        focus.requesters.remove(item.id)
                        focus.fieldStates.remove(item.id)
                    }
                }
                ListItemRow(
                    item = item,
                    marker = marker.forItem(itemIndex + 1),
                    state = state,
                    editor = editor,
                    isLocked = isLocked,
                    fieldModifier = Modifier.focusRequester(requester),
                )
            }
        }
    }
}

/**
 * How a list marks its items: checkboxes, bullets or numbers in a hanging-indent column of
 * [width]; [number] is the item's own.
 */
private data class ListMarker(val type: BlockType, val width: Dp, val number: Int = 0) {
    fun forItem(number: Int) = copy(number = number)
}

@Composable
private fun ListItemRow(
    item: ItemListBlock,
    marker: ListMarker,
    state: TextFieldState,
    editor: ListEditor,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val checklist = marker.type == BlockType.LIST_CHECK
    val placeholder = stringResource(R.string.list_item_placeholder)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = if (checklist) Alignment.Top else Alignment.CenterVertically,
    ) {
        when (marker.type) {
            BlockType.LIST_CHECK -> CheckMark(
                checked = item.isCheck,
                label = state.text.toString().ifBlank { placeholder },
                enabled = !isLocked,
                onToggle = { editor.setChecked(item.id, it) },
            )

            BlockType.LIST_NUMBER -> Marker("${marker.number}.", marker.width, EnclyTheme.typography.dataSmall)

            else -> Marker("•", marker.width, listTextStyle())
        }
        ListItemField(
            state = state,
            checked = checklist && item.isCheck,
            placeholder = placeholder,
            isLocked = isLocked,
            onEnter = { editor.onEnter(item.id, state.text.isEmpty()) },
            modifier = Modifier
                .weight(1f)
                .then(if (checklist) Modifier.padding(vertical = EnclyTheme.spacing.xs) else Modifier.alignByBaseline())
                .then(fieldModifier)
                .onPreviewKeyEvent { event ->
                    val selection = state.selection
                    KeyboardUtils.handleKeyEvent(
                        event = event,
                        text = state.text,
                        cursorPosition = if (selection.collapsed) selection.start else -1,
                        onBackspaceEmpty = { editor.removeEmpty(item.id) },
                        onNavigateUp = { editor.moveFocus(item.id, up = true) },
                        onNavigateDown = { editor.moveFocus(item.id, up = false) },
                        onEnterPressed = { editor.onEnter(item.id, state.text.isEmpty()) },
                    )
                },
        )
    }
}

/** The list's edits, all by item id, so a pending edit can never land on the wrong item. */
private class ListEditor(
    private val block: Block.ListBlock,
    private val actions: () -> BlockActions,
    private val focusItem: (ItemFocus) -> Unit,
) {
    private val items: List<ItemListBlock> get() = block.items.value

    private fun update(mergeable: Boolean, change: MutableList<ItemListBlock>.() -> Unit) {
        actions().onListItemsChanged(items.toMutableList().apply(change), mergeable)
    }

    fun setText(itemId: String, text: String) = update(mergeable = true) {
        val at = indexOfFirst { it.id == itemId }
        if (at >= 0) this[at] = this[at].copy(value = text)
    }

    fun setChecked(itemId: String, checked: Boolean) = update(mergeable = false) {
        val at = indexOfFirst { it.id == itemId }
        if (at >= 0) this[at] = this[at].copy(isCheck = checked)
    }

    /**
     * Enter: a new item after this one. On an empty last item it leaves the list instead: the
     * item goes, and a paragraph follows the list (an only item turns the list into one).
     */
    fun onEnter(itemId: String, empty: Boolean): Boolean {
        val at = items.indexOfFirst { it.id == itemId }
        if (at < 0) return true
        when {
            !empty || at < items.lastIndex -> {
                val added = ItemListBlock("")
                update(mergeable = false) { add(at + 1, added) }
                focusItem(ItemFocus(added.id, atEnd = false))
            }

            items.size == 1 -> actions().onReplaceBlock(Block.TextBlock())

            else -> {
                update(mergeable = false) { removeAt(at) }
                actions().onAddParagraph()
            }
        }
        return true
    }

    /** Backspace in an empty item removes it (the whole list with its only item) and moves up. */
    fun removeEmpty(itemId: String) {
        val at = items.indexOfFirst { it.id == itemId }
        if (at < 0) return
        if (items.size == 1) {
            actions().onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE_LIST)
            return
        }
        val previous = items.getOrNull(at - 1) ?: items.getOrNull(at + 1)
        update(mergeable = false) { removeAt(at) }
        previous?.let { focusItem(ItemFocus(it.id, atEnd = true)) }
    }

    /** Up/Down: the item above or below, then the block above or below the list. */
    fun moveFocus(itemId: String, up: Boolean): Boolean {
        val at = items.indexOfFirst { it.id == itemId }
        val neighbour = items.getOrNull(if (up) at - 1 else at + 1)
        return when {
            neighbour != null -> {
                focusItem(ItemFocus(neighbour.id, atEnd = up))
                true
            }

            up -> actions().navigateToPrevious()

            else -> {
                // The last item of the last block: a new item, as Enter would add.
                if (!actions().navigateToNext()) onEnter(itemId, empty = false)
                true
            }
        }
    }
}

@Composable
private fun listTextStyle(): TextStyle {
    val fontStyles = rememberFontStyles()
    return MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = fontStyles.families.body,
        fontSize = fontStyles.sizes.list,
    )
}

/** A bullet or number in its hanging-indent column, on the item's first baseline. */
@Composable
private fun RowScope.Marker(text: String, width: Dp, style: TextStyle) {
    val fontStyles = rememberFontStyles()
    Text(
        text = text,
        style = style.copy(fontSize = fontStyles.sizes.list),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .width(width)
            .alignByBaseline(),
    )
}

/**
 * The checklist box, flush with the paragraphs above, and its toggle target: box and gap wide,
 * as tall as a one-line row (40, spec §3.3). Announced with the item's text, so TalkBack says
 * what is checked.
 */
@Composable
private fun CheckMark(checked: Boolean, label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val box = CheckboxSize.LARGE.size
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .width(box + EnclyTheme.spacing.rowGap)
            .heightIn(min = box + EnclyTheme.spacing.xs * 2)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = label },
    ) {
        EnclyCheckbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ListItemField(
    state: TextFieldState,
    checked: Boolean,
    placeholder: String,
    isLocked: Boolean,
    onEnter: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val base = listTextStyle()
    // Checked items stay editable; they are only muted and struck through.
    val style = if (checked) {
        base.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
    } else {
        base
    }
    BasicTextField(
        state = state,
        readOnly = isLocked,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = WritingKeyboard.copy(imeAction = ImeAction.Done),
        onKeyboardAction = { onEnter() },
        modifier = modifier,
        decorator = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.text.isEmpty() && !isLocked) {
                    Text(text = placeholder, style = base.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                innerTextField()
            }
        },
    )
}
